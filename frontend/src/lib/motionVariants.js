export const quickTransition = { duration: .18, ease: 'easeOut' }

export const backdropMotion = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: .16, ease: 'easeOut' },
}

export const fadeUp = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -4 },
  transition: quickTransition,
}

export const popoverMotion = {
  initial: { opacity: 0, scale: .97, y: -4 },
  animate: { opacity: 1, scale: 1, y: 0 },
  exit: { opacity: 0, scale: .98, y: -3 },
  transition: { duration: .14, ease: 'easeOut' },
}

export const modalMotion = {
  initial: { opacity: 0, scale: .98, y: 16 },
  animate: { opacity: 1, scale: 1, y: 0 },
  exit: { opacity: 0, scale: .98, y: 10 },
  transition: quickTransition,
}

export const toastMotion = {
  initial: { opacity: 0, x: 24, scale: .97 },
  animate: { opacity: 1, x: 0, scale: 1 },
  exit: { opacity: 0, x: 18, scale: .97 },
  transition: { duration: .16, ease: 'easeOut' },
}
