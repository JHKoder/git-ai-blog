/// <reference types="vitest/config" />   // ← 이 줄이 중요!

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
                manualChunks: (id: string) => {
                    // React 핵심 라이브러리
                    if (id.includes('react') || id.includes('react-dom') || id.includes('react-router')) {
                        return 'vendor-react';
                    }
                    // Mermaid 관련 대형 라이브러리 강제 분리 (빌드 속도에 가장 큰 영향)
                    if (id.includes('mermaid') ||
                        id.includes('cytoscape') ||
                        id.includes('katex') ||
                        id.includes('dagre') ||
                        id.includes('d3')) {
                        return 'vendor-mermaid';
                    }
                    // 나머지 node_modules
                    if (id.includes('node_modules')) {
                        return 'vendor';
                    }
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
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: './src/test/setup.ts',
        pool: 'vmThreads',
    },
})