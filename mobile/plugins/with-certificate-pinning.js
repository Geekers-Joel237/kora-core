/**
 * Plugin de configuration Expo — épinglage de certificat.
 *
 * Pose les deux mécanismes déclaratifs décrits dans `certificate-pinning.js`
 * au moment du `prebuild`. Aucun code applicatif n'est impliqué : l'épinglage
 * vit dans la configuration native, là où le système l'applique à **toutes**
 * les connexions, y compris celles des bibliothèques tierces.
 *
 * Sans empreinte configurée, le plugin ne fait **rien** — pas d'échafaudage
 * vide qui donnerait l'illusion d'une protection en place. L'état réel est
 * rapporté par `npm run audit:pinning`.
 *
 * Configuration, dans `app.json` :
 *
 * ```json
 * "extra": {
 *   "certificatePinning": {
 *     "domain": "api.kora.cm",
 *     "pins": ["<SPKI-SHA256-base64>", "<empreinte de secours>"],
 *     "expiration": "2027-01-01"
 *   }
 * }
 * ```
 */

const { withAndroidManifest, withDangerousMod, withInfoPlist } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');
const {
  buildNetworkSecurityConfig,
  buildPinnedDomains,
  normalizePinningConfig,
} = require('./certificate-pinning');

const CONFIG_FILE_NAME = 'network_security_config';

/**
 * @param {import('@expo/config-types').ExpoConfig} config
 * @returns {import('@expo/config-types').ExpoConfig}
 */
function withCertificatePinning(config) {
  const pinning = normalizePinningConfig(config.extra?.certificatePinning);

  if (!pinning) return config;

  let next = withDangerousMod(config, [
    'android',
    (androidConfig) => {
      const xmlDir = path.join(
        androidConfig.modRequest.platformProjectRoot,
        'app',
        'src',
        'main',
        'res',
        'xml',
      );
      fs.mkdirSync(xmlDir, { recursive: true });
      fs.writeFileSync(
        path.join(xmlDir, `${CONFIG_FILE_NAME}.xml`),
        buildNetworkSecurityConfig(pinning),
        'utf8',
      );
      return androidConfig;
    },
  ]);

  next = withAndroidManifest(next, (androidConfig) => {
    const application = androidConfig.modResults.manifest.application?.[0];
    if (application) {
      application.$['android:networkSecurityConfig'] = `@xml/${CONFIG_FILE_NAME}`;
      // Sans cela, l'épinglage se contourne par une simple rétrogradation en HTTP.
      application.$['android:usesCleartextTraffic'] = 'false';
    }
    return androidConfig;
  });

  next = withInfoPlist(next, (iosConfig) => {
    const ats = iosConfig.modResults.NSAppTransportSecurity ?? {};
    iosConfig.modResults.NSAppTransportSecurity = {
      ...ats,
      NSPinnedDomains: {
        ...(ats.NSPinnedDomains ?? {}),
        ...buildPinnedDomains(pinning),
      },
    };
    return iosConfig;
  });

  return next;
}

module.exports = withCertificatePinning;
