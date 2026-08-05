import * as LeafletNamespace from 'leaflet';
import 'leaflet.markercluster';

type LeafletApi = typeof import('leaflet');

function resolveLeaflet(): LeafletApi {
  const globalL = (globalThis as unknown as { L?: LeafletApi }).L;
  if (globalL && typeof (globalL as unknown as { markerClusterGroup?: unknown }).markerClusterGroup === 'function') {
    return globalL;
  }

  const ns = LeafletNamespace as unknown as { default?: LeafletApi } & LeafletApi;
  return ns.default ?? ns;
}

/** Instância Leaflet com markercluster (UMD global se existir, senão ESM). */
export const L = resolveLeaflet();
