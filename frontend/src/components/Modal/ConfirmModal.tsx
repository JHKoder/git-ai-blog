import styles from './ConfirmModal.module.css'

interface Props {
  message: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmModal({ message, onConfirm, onCancel }: Props) {
  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <p>{message}</p>
        <div className={styles.actions}>
          <button className={styles.confirmBtn} onClick={onConfirm}>확인</button>
          <button className={styles.cancelBtn} onClick={onCancel}>취소</button>
        </div>
      </div>
    </div>
  )
}
