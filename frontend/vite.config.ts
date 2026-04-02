/// <reference types="vitest/config" />   // ← 이 줄 추가 (test 옵션 인식용)'

import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],

    // ==================== 빌드 최적화 ====================
    build: {
        sourcemap: false,
        minify: 'esbuild',
        target: 'es2022',
        chunkSizeWarningLimit: 1600,


        rollupOptions: {
            output: {
                manualChunks: {
                    // 큰 라이브러리들을 별도 청크로 분리 → 빌드/로딩 속도 향상
                    vendor: ['react', 'react-dom'],
                    router: ['react-router-dom'],
                    // 필요하면 더 추가: ['axios', '@tanstack/react-query'] 등
                },
            },
        },
    },

    // ==================== 개발 서버 Proxy ====================
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/oauth2/authorization': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/login/oauth2': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },

    // ==================== Vitest 설정 ====================
    // @ts-ignore
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: './src/test/setup.ts',
        pool: 'vmThreads',
        typecheck: {
            tsconfig: './tsconfig.test.json',
        },
    },
})