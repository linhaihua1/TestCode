import js from '@eslint/js';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import vuePlugin from 'eslint-plugin-vue';
import prettierConfig from 'eslint-config-prettier';

export default [
  // 忽略目录
  {
    ignores: [
      'node_modules/**',
      'dist/**',
      'build/**',
      'coverage/**',
      '*.d.ts',
    ],
  },

  // 基础 JS 规则
  js.configs.recommended,

  // TypeScript 规则
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.vue'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      // 放宽部分规则，避免影响现有代码
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-non-null-assertion': 'off',
      // TS 环境下由编译器负责未定义/类型检查，关闭 JS 时代的 no-undef
      'no-undef': 'off',
      // shims-vue.d.ts 等声明文件使用 `{}` 描述任意对象是常见模式，关闭空对象类型检查
      '@typescript-eslint/no-empty-object-type': 'off',
    },
  },

  // Vue 规则（官方强推荐配置，Vue 3）
  ...vuePlugin.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tsParser,
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      // Vue 风格放宽，避免过度约束
      'vue/multi-word-component-names': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      // 本平台 step 等对象 prop 与父组件共享引用（编辑即改），
      // 仅禁止重新赋值 prop 本身，允许修改对象内部属性
      'vue/no-mutating-props': ['error', { shallowOnly: true }],
    },
  },

  // Prettier 规则置底，关闭与 Prettier 冲突的格式化规则
  prettierConfig,
];
