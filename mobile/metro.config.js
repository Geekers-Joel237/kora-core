const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

/**
 * Exclusion du mode validation en production — `docs/10-validation-mode.md` §12.
 *
 * **Une garde à l'exécution ne suffit pas.** Metro construit son graphe de
 * dépendances à partir de la sortie de Babel, avant toute minification : un
 * `import()` gardé par `if (!DEV_MODE)` reste une arête du graphe, et le module
 * est empaqueté même si l'appel est ensuite éliminé. Mesuré au lot 10 — les
 * onze modules de `src/devtools/` figuraient dans le bundle de production.
 *
 * La résolution est donc détournée à la racine : `src/devtools/index` pointe
 * vers un substitut inerte, et Metro ne descend jamais plus bas.
 *
 * Vérification : `npm run audit:bundle`.
 */
const config = getDefaultConfig(__dirname);

const DEVTOOLS_ENTRY = path.join(__dirname, 'src', 'devtools', 'index.tsx');
const DEVTOOLS_STUB = path.join(__dirname, 'src', 'devtools', 'index.production.tsx');

const isProduction = process.env.EXPO_PUBLIC_ENV === 'production';

if (isProduction) {
  const defaultResolveRequest = config.resolver.resolveRequest;

  config.resolver.resolveRequest = (context, moduleName, platform) => {
    const resolve = defaultResolveRequest ?? context.resolveRequest;
    const resolved = resolve(context, moduleName, platform);

    if (resolved && resolved.type === 'sourceFile' && resolved.filePath === DEVTOOLS_ENTRY) {
      return { type: 'sourceFile', filePath: DEVTOOLS_STUB };
    }

    return resolved;
  };
}

module.exports = config;
