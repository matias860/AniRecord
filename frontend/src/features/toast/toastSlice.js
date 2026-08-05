import { createSlice, nanoid } from '@reduxjs/toolkit'

const toastSlice = createSlice({
  name: 'toast',
  initialState: { items: [] },
  reducers: {
    addToast: {
      reducer: (state, action) => {
        if (state.items.some((item) => item.message === action.payload.message)) return
        state.items.push(action.payload)
        if (state.items.length > 4) state.items.shift()
      },
      prepare: ({ message, type = 'info', title }) => ({ payload: { id: nanoid(), message, type, title } }),
    },
    removeToast: (state, action) => { state.items = state.items.filter((item) => item.id !== action.payload) },
  },
})

export const { addToast, removeToast } = toastSlice.actions
export default toastSlice.reducer
