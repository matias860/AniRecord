import { createApi } from '@reduxjs/toolkit/query/react'
import apiClient from './apiClient.js'
import {
  cachedAniListError,
  clearAniListOutage,
  isAniListServiceError,
  isAniListTemporarilyUnavailable,
  rememberAniListOutage,
} from './anilistAvailability.js'

const errorPayload = (error, unavailable = false) => ({
  status: error.status ?? error.response?.status ?? 'FETCH_ERROR',
  data: { message: error.message || 'No se pudo completar la solicitud.', unavailable },
})

async function aniListRequest(config, api) {
  if (isAniListTemporarilyUnavailable()) {
    const cached = cachedAniListError()
    return { error: { status: 'ANILIST_UNAVAILABLE', data: cached } }
  }
  try {
    const response = await apiClient.request({ ...config, signal: api.signal })
    clearAniListOutage()
    return { data: response.data }
  } catch (error) {
    const unavailable = isAniListServiceError(error)
    if (unavailable) rememberAniListOutage()
    return { error: errorPayload(error, unavailable) }
  }
}

export const anirecordApi = createApi({
  reducerPath: 'anirecordApi',
  baseQuery: () => ({ error: { status: 'UNUSED', data: { message: 'Consulta no configurada.' } } }),
  keepUnusedDataFor: 10 * 60,
  refetchOnReconnect: true,
  endpoints: (builder) => ({
    getMediaDetail: builder.query({
      queryFn: (anilistId, api) => aniListRequest({ url: `/anilist/media/${anilistId}` }, api),
    }),
    getCatalog: builder.query({
      queryFn: ({ type, page = 1, filters = {} }, api) => aniListRequest({
        url: '/anilist/catalog',
        params: { type, page, perPage: 24, ...filters },
        paramsSerializer: { indexes: null },
      }, api),
    }),
    getCatalogCategories: builder.query({
      queryFn: (_, api) => aniListRequest({ url: '/anilist/catalog/categories' }, api),
      keepUnusedDataFor: 60 * 60,
    }),
  }),
})

export const {
  useGetCatalogQuery,
  useGetCatalogCategoriesQuery,
  useGetMediaDetailQuery,
  usePrefetch,
} = anirecordApi
