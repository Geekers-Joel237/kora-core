module.exports = function (api) {
  api.cache(true);

  const isProduction = process.env.NODE_ENV === 'production';

  return {
    presets: [['babel-preset-expo', { jsxImportSource: 'react' }]],
    plugins: [
      // NFR-42 / 06-architecture §6.3 — aucun log ne survit à une build de production.
      ...(isProduction ? ['transform-remove-console'] : []),

      // Reanimated 4 : le plugin vit désormais dans react-native-worklets.
      // DOIT rester le dernier plugin de la liste.
      'react-native-worklets/plugin',
    ],
  };
};
