import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: { ...globals.browser, ...globals.node },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': 'off',

      // Rules of hooks must never be violated — kept as errors.
      'react-hooks/rules-of-hooks': 'error',
      // Missing deps are worth flagging but the codebase has many intentional
      // omissions (stable setters, one-time effects) — warn, not error.
      'react-hooks/exhaustive-deps': 'warn',

      // `any` is discouraged but still used pragmatically in a few places
      // (axios error narrowing, third-party payloads) — warn, not error.
      '@typescript-eslint/no-explicit-any': 'warn',
      // Widely relied upon in the codebase for optional API fields / DTOs.
      '@typescript-eslint/no-non-null-assertion': 'off',
      // Underscore-prefixed args/vars are used to mark intentionally unused values.
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    files: ['**/*.test.{ts,tsx}', 'src/test/**/*.{ts,tsx}'],
    languageOptions: {
      globals: { ...globals.vitest },
    },
  },
  {
    files: ['*.config.{js,ts}'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },
)
