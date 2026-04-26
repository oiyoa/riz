import { defineConfig } from 'vite';
import { viteSingleFile } from 'vite-plugin-singlefile';
import { createHtmlPlugin } from 'vite-plugin-html';

export default defineConfig({
  plugins: [
    viteSingleFile(),
    createHtmlPlugin({
      minify: {
        collapseWhitespace: true,
        keepClosingSlash: false,
        removeComments: true,
        removeRedundantAttributes: true,
        removeScriptTypeAttributes: true,
        removeStyleLinkTypeAttributes: true,
        useShortDoctype: true,
        minifyCSS: true,
        minifyJS: true,
        removeAttributeQuotes: true,
        collapseBooleanAttributes: true,
        collapseInlineTagWhitespace: true,
        sortAttributes: true,
        sortClassName: true,
        removeOptionalTags: true,
        removeEmptyAttributes: true
      }
    }),
  ],
  build: {
    target: 'esnext',
    cssMinify: 'lightningcss',
    minify: 'terser',
    terserOptions: {
      compress: {
        passes: 3,
        drop_console: true,
        drop_debugger: true,
        pure_getters: true,
        unsafe: true,
        unsafe_arrows: true,
        unsafe_comps: true,
        unsafe_math: true,
        unsafe_methods: true,
        unsafe_symbols: true
      },
      format: {
        comments: false
      },
      mangle: {
        toplevel: true
      }
    }
  }
});
