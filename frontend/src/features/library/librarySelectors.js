import { createSelector } from '@reduxjs/toolkit'

const selectLibraryItems = (state) => state.library.items

const topRatedByType = (mediaType) => createSelector(
  [selectLibraryItems],
  (items) => items
    .filter((item) => item.mediaType === mediaType && item.personalScore != null)
    .sort((a, b) => Number(b.personalScore) - Number(a.personalScore) || new Date(b.updatedAt) - new Date(a.updatedAt))
    .slice(0, 5),
)

export const selectTopRatedAnime = topRatedByType('ANIME')
export const selectTopRatedManga = topRatedByType('MANGA')
