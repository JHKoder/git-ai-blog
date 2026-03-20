# Squash and Merge 정책

이 프로젝트는 main 브랜치에 대해 **Squash and Merge**만 허용한다.

## GitHub 저장소 설정 방법

`Settings → General → Pull Requests`:
- [ ] Allow merge commits → **비활성화**
- [x] Allow squash merging → **활성화**
- [ ] Allow rebase merging → **비활성화**

## Branch Protection Rule (Settings → Branches → main)

```
- [x] Require a pull request before merging
  - [x] Require approvals: 0 (1인 프로젝트)
  - [x] Require linear history (Squash and merge 강제)
- [x] Require status checks to pass before merging
  - Required checks: test-backend, test-frontend
- [x] Do not allow bypassing the above settings
```

## 목적

- 커밋 히스토리를 깔끔하게 유지 (기능 단위로 1 커밋)
- main 브랜치 히스토리를 선형으로 유지
- 테스트 통과 필수로 코드 품질 보장
