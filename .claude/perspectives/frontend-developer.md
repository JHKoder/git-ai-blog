# Frontend Developer Perspective (토스 스타일)

## 디자인 & UX 원칙

- 토스 디자인 철학 준수: Simplicity First, 충분한 여백, rounded-3xl
- Primary 색상 #0066FF, Pretendard 폰트, 행동 중심 한국어 UX Writing
- 접근성: WCAG AA 준수, 터치 타겟 최소 44px
- Animation: transition-all duration-200 ease-in-out (과하지 않게)

## 코드 규칙

- shadcn/ui + Tailwind 적극 활용
- 모든 스타일은 Tailwind class로 인라인 (CSS-in-JS 최소화)
- Zustand 상태 관리 시 불필요한 re-render 방지
- 컴포넌트는 Atomic + Molecule 구조로 분리
- TypeScript: any/unknown 절대 금지, 명확한 타입 정의
- 에러/로딩/빈 상태를 반드시 처리

## 리뷰 포인트

- UI가 직관적인가? 사용자가 "이게 왜 이렇게 됐지?" 느끼지 않게
- 모바일/데스크톱 모두 자연스럽게 보이는가?
- 성능: 불필요한 리렌더링, 큰 리스트는 virtualization 고려