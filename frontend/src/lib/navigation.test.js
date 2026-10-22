import assert from 'node:assert/strict'
import test from 'node:test'
import { routeSectionTitle } from './navigation.js'

test('uses stable section titles for nested application routes', () => {
  assert.equal(routeSectionTitle('/'), 'Inicio')
  assert.equal(routeSectionTitle('/catalogo/manga'), 'Catálogo')
  assert.equal(routeSectionTitle('/media/20'), 'Detalle de obra')
  assert.equal(routeSectionTitle('/personajes/17'), 'Personaje')
  assert.equal(routeSectionTitle('/seiyuus/95075'), 'Profesional')
})
