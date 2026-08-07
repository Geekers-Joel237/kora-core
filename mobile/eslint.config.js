// @ts-check
const expoConfig = require('eslint-config-expo/flat');
const prettier = require('eslint-config-prettier');

/**
 * Propriétés de style dont la valeur DOIT venir d'un token.
 * Voir 02-design-system.md §4 (espacement), §5 (forme), §3 (typographie).
 */
const TOKENISED_STYLE_PROPS = [
  'margin',
  'marginTop',
  'marginBottom',
  'marginLeft',
  'marginRight',
  'marginHorizontal',
  'marginVertical',
  'padding',
  'paddingTop',
  'paddingBottom',
  'paddingLeft',
  'paddingRight',
  'paddingHorizontal',
  'paddingVertical',
  'gap',
  'rowGap',
  'columnGap',
  'borderRadius',
  'borderTopLeftRadius',
  'borderTopRightRadius',
  'borderBottomLeftRadius',
  'borderBottomRightRadius',
  'fontSize',
  'lineHeight',
  'letterSpacing',
  'borderWidth',
].join('|');

/** Valeurs 0 et 1 tolérées : neutres, et 1 sert aux traits d'un pixel. */
const FORBIDDEN_NUMERIC = /^-?(?!0$|1$)\d+(\.\d+)?$/.source;

module.exports = [
  ...expoConfig,
  prettier,

  {
    ignores: [
      'node_modules/**',
      '.expo/**',
      'dist/**',
      'docs/**',
      'android/**',
      'ios/**',
      'babel.config.js',
      'eslint.config.js',
    ],
  },

  {
    files: ['**/*.{ts,tsx}'],
    rules: {
      // ── NFR-10 : aucune valeur de style littérale hors du fichier de tokens ──
      'no-restricted-syntax': [
        'error',
        {
          selector: "Literal[value=/^#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/]",
          message:
            'Couleur hexadécimale littérale interdite. Utiliser un token de src/theme/tokens.ts. Voir 02-design-system.md §8.',
        },
        {
          selector: "Literal[value=/^rgba?\\(/]",
          message:
            'Couleur rgb/rgba littérale interdite. Utiliser un token de src/theme/tokens.ts. Voir 02-design-system.md §8.',
        },
        {
          selector: `Property[key.name=/^(${TOKENISED_STYLE_PROPS})$/] > Literal[raw=/${FORBIDDEN_NUMERIC}/]`,
          message:
            "Valeur d'espacement, de rayon ou de typographie littérale interdite. Utiliser space/radius/type de src/theme/tokens.ts. Voir 02-design-system.md §4, §5, §3.",
        },
        {
          selector: 'ImportDeclaration[source.value=/^(native-base|tamagui|@gluestack-ui|react-native-paper|react-native-elements)/]',
          message:
            'Bibliothèque de composants tierce interdite. Le design system est écrit à la main. Voir README.md règle 2.',
        },
        {
          selector: "ImportSpecifier[imported.name='TouchableOpacity'], ImportSpecifier[imported.name='TouchableHighlight'], ImportSpecifier[imported.name='TouchableWithoutFeedback']",
          message:
            'Touchable* interdit : le retour au toucher passerait par le pont JS. Utiliser @/components/primitives/Pressable. Voir 03-motion-and-feel.md §7.1.',
        },
        {
          selector: "ImportSpecifier[imported.name='Animated'][parent.source.value='react-native']",
          message:
            "Animated de React Native interdit. Utiliser react-native-reanimated. Voir 03-motion-and-feel.md §7.1.",
        },
        {
          selector: "ImportSpecifier[imported.name='LayoutAnimation']",
          message:
            'LayoutAnimation interdit. Utiliser les animations de Reanimated. Voir 03-motion-and-feel.md §7.1.',
        },
      ],

      '@typescript-eslint/no-explicit-any': 'error',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'import/no-unresolved': 'off',
    },
  },

  // Le fichier de tokens est la seule source légitime de valeurs littérales.
  {
    files: ['src/theme/**/*.{ts,tsx}'],
    rules: { 'no-restricted-syntax': 'off' },
  },

  /**
   * `react-hooks/immutability` (règles du React Compiler) interdit de muter une
   * valeur déjà utilisée dans un effet. Reanimated repose entièrement sur cette
   * mutation : une `SharedValue` est écrite depuis un effet ET depuis un
   * worklet de geste, et c'est l'usage prévu par sa conception.
   *
   * La règle ne modélise pas les valeurs partagées. On la désactive donc pour
   * les composants qui en pilotent, et pour eux seulement — jamais globalement.
   */
  {
    files: [
      'src/components/**/*.{ts,tsx}',
      'src/devtools/**/*.{ts,tsx}',
      'app/**/*.{ts,tsx}',
    ],
    rules: { 'react-hooks/immutability': 'off' },
  },

  // Les devtools peuvent journaliser : ils sont exclus du bundle de production.
  {
    files: ['src/devtools/**/*.{ts,tsx}'],
    rules: { 'no-console': 'off' },
  },

  {
    files: ['**/*.test.{ts,tsx}', 'jest.setup.ts'],
    rules: { 'no-restricted-syntax': 'off', '@typescript-eslint/no-explicit-any': 'off' },
  },
];
