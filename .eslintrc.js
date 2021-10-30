module.exports = {
  root: true,
  extends: [
    "airbnb",
    "plugin:react-hooks/recommended",
    "plugin:@typescript-eslint/eslint-recommended",
    "plugin:@typescript-eslint/recommended",
  ],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaFeatures: {
      jsx: true,
      modules: true,
    },
    ecmaVersion: 12,
    sourceType: "module",
    files: ["*.ts", "*.tsx"],
    project: "./tsconfig.eslint.json",
  },
  globals: {
    Atomics: "readonly",
    SharedArrayBuffer: "readonly",
    fetch: true,
    "react-native/react-native": true,
    __DEV__: true,
  },
  plugins: [
    "react",
    "react-native",
    "@typescript-eslint/eslint-plugin",
  ],
};
