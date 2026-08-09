import { addToast } from '../features/toast/toastSlice.js'

const successMessages = {
  'profile/save/fulfilled': 'Perfil guardado correctamente.',
  'favorites/addPerson/fulfilled': 'Agregado a favoritos.',
  'favorites/removePerson/fulfilled': 'Quitado de favoritos.',
  'library/synchronizeAniList/fulfilled': 'Biblioteca sincronizada con AniList.',
  'library/add/fulfilled': 'Título agregado a tu biblioteca.',
  'library/remove/fulfilled': 'Título eliminado de tu biblioteca.',
}

export const toastMiddleware = (store) => (next) => (action) => {
  const result = next(action)
  if (action.type.endsWith('/rejected') && !action.meta?.condition && !action.meta?.aborted) {
    store.dispatch(addToast({ type: 'error', title: 'Algo salió mal', message: action.payload?.message || action.error?.message || 'No se pudo completar la operación.' }))
  } else if (successMessages[action.type]) {
    store.dispatch(addToast({ type: 'success', title: 'Listo', message: successMessages[action.type] }))
  }
  return result
}
