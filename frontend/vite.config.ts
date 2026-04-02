/// <reference types="vitest" />
import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],

    // ==================== 빌드 속도 & 크기 최적화 ====================
    build: {
        sourcemap: false,                    // 프로덕션에서는 sourcemap 끄기 (빌드 속도 크게 ↑)
        minify: 'esbuild',                   // esbuild가 terser보다 훨씬 빠름 (기본값이지만 명시 추천)
        target: 'es2022',                    // 최신 브라우저 대상으로 빌드 (작고 빠름)
        chunkSizeWarningLimit: 1000,         // 청크 크기 경고 기준 완화

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

    // ==================== 개발 서버 (기존 유지) ====================
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

    // ==================== Vitest 설정 (기존 유지) ====================
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: './src/test/setup.ts',
        pool: 'vmThreads',          // vmThreads가 보통 더 빠름
    },
})