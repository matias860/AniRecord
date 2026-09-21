export const mangaFormats = [{ value: '', label: 'Todos' }, { value: 'MANGA', label: 'Manga' }, { value: 'NOVEL', label: 'Light novel' }, { value: 'ONE_SHOT', label: 'One-shot' }]
export const animeFormats = [{ value: '', label: 'Todos' }, { value: 'TV', label: 'Serie de TV' }, { value: 'MOVIE', label: 'Película' }, { value: 'OVA', label: 'OVA' }, { value: 'ONA', label: 'ONA' }, { value: 'TV_SHORT', label: 'Serie corta' }, { value: 'SPECIAL', label: 'Especial' }, { value: 'MUSIC', label: 'Video musical' }]
export const formatsFor = (type) => type === 'MANGA' ? mangaFormats : animeFormats
