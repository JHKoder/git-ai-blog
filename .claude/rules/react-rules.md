# React Rules - 토스스러운 디자인 & 코드 컨벤션 (2026)

## 핵심 제약

- **`any` / `unknown` 타입 사용 금지**
- **기술 변경금지**

---

## 기술 스택

| 영역       | 기술                                    |
|----------|---------------------------------------|
| 프레임워크    | React 18 + TypeScript + Vite 5        |
| 상태관리     | Zustand + immer 미들웨어                  |
| HTTP     | Axios (JWT interceptor 자동 주입)         |
| 스타일      | CSS Modules + CSS 변수 (다크/라이트 모드)      |
| 라우팅      | React Router v7                       |
| Markdown | react-markdown + remark-gfm + Mermaid |
| SQL 에디터  | @monaco-editor/react                  |
| 플로우 그래프  | @xyflow/react (ReactFlow)             |

## 1. 토스 디자인 철학 (반드시 준수)

- **Simplicity First**: 불필요한 요소는 모두 제거. "적을수록 강하다"
- **일관성**: 동일한 패턴은 100% 동일하게 구현
- **접근성 최우선**: WCAG AA 이상, 터치 타겟 최소 44px, 충분한 contrast
- **Fintech Trust**: 청량한 블루, 여백이 넉넉하고 깔끔한 레이아웃, 빠른 피드백
- **한국 사용자 중심**: Pretendard 폰트, 자연스러운 한국어 UX 라이팅

## 2. Design Tokens (Tailwind 또는 CSS 변수로 반드시 정의)

- Primary: #0066FF (토스 메인 블루)
- Primary-50: #E6F0FF
- Gray-900 ~ Gray-100: 토스 그레이 스케일
- Success: #00C853
- Warning: #FF9800
- Error: #FF3B5C
- Background: #FFFFFF (Light) / #0A0A0A (Dark 지원 시)
- Radius: rounded-2xl (기본), rounded-3xl (카드), rounded-full (버튼)
- Shadow: shadow-sm (미세), shadow-md (카드)

## 3. Typography

- Font: Pretendard (또는 system-ui, -apple-system)
- Heading: font-bold, tracking-[-0.02em]
- Body: font-medium
- Caption: text-sm text-gray-500

## 4. 컴포넌트 규칙 (토스스러운 느낌)

- Button:
    - 기본: bg-[#0066FF] hover:bg-[#0052CC] text-white font-semibold rounded-3xl px-6 py-3.5
    - Secondary: border border-gray-300 hover:bg-gray-50
    - 크기: h-12 이상 (터치 친화적)
- Card: bg-white border border-gray-100 rounded-3xl shadow-sm p-6
- Input: rounded-2xl border border-gray-200 focus:border-[#0066FF] focus:ring-0
- Spacing: gap-6 또는 gap-8 위주 (8px grid 기반)
- Layout: flex + grid 적극 사용, max-w-3xl 중앙 정렬 선호

## 5. 코드 규칙

- shadcn/ui 또는 Tailwind + Radix UI 조합 강력 권장
- 컴포넌트는 Atomic + Molecule 구조로 분리
- 모든 스타일은 Tailwind class로 인라인 (CSS-in-JS 최소화)
- Dark mode 지원 시 class strategy 사용
- Animation: transition-all duration-200 ease-in-out (부드럽게)

## 6. UX Writing

- 버튼: "확인하기", "송금하기", "저장하기"처럼 행동 중심 + 한국어 자연스럽게
- 에러 메시지: 친절하고 구체적으로 ("송금 한도를 초과했어요. 3분 후 다시 시도해주세요.")

모든 React 파일 작성 시 위 토큰과 철학을 100% 반영한다.