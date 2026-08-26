// Light-level mob spawn overlay capture, geometry, and mesh lifecycle.

#include "mcrtx/core/remix_renderer.hpp"
#include "mcrtx/chunks/remix_chunk_policy.hpp"
#include "mcrtx/core/remix_geometry_common.hpp"
#include "mcrtx/core/remix_render_common.hpp"
#include "mcrtx/lifecycle/perf_log.hpp"

#include <cstddef>
#include <cstdint>
#include <vector>

namespace mcrtx {

using namespace mcrtx::detail;
using namespace mcrtx::chunk;
using namespace mcrtx::geometry;

namespace {

constexpr std::uint64_t kLightLevelOverlayMeshHashSeed = 0x4D435254584C4C00ull;

std::uint64_t makeLightLevelOverlayMeshHash(std::uint64_t sequence) {
  return kLightLevelOverlayMeshHashSeed | (sequence & 0x0000FFFFFFFFFFFFull);
}

void appendQuad(
    const float p0[3], const float p1[3], const float p2[3], const float p3[3],
    const float normal[3],
    std::uint32_t color,
    std::vector<remixapi_HardcodedVertex>& vertices,
    std::vector<std::uint32_t>& indices) {
  const std::uint32_t base = static_cast<std::uint32_t>(vertices.size());
  constexpr float u = 0.03125f;
  constexpr float v = 0.03125f;

  remixapi_HardcodedVertex v0 {};
  v0.position[0] = p0[0]; v0.position[1] = p0[1]; v0.position[2] = p0[2];
  v0.normal[0] = normal[0]; v0.normal[1] = normal[1]; v0.normal[2] = normal[2];
  v0.texcoord[0] = u; v0.texcoord[1] = v;
  v0.color = color;

  remixapi_HardcodedVertex v1 {};
  v1.position[0] = p1[0]; v1.position[1] = p1[1]; v1.position[2] = p1[2];
  v1.normal[0] = normal[0]; v1.normal[1] = normal[1]; v1.normal[2] = normal[2];
  v1.texcoord[0] = u; v1.texcoord[1] = v;
  v1.color = color;

  remixapi_HardcodedVertex v2 {};
  v2.position[0] = p2[0]; v2.position[1] = p2[1]; v2.position[2] = p2[2];
  v2.normal[0] = normal[0]; v2.normal[1] = normal[1]; v2.normal[2] = normal[2];
  v2.texcoord[0] = u; v2.texcoord[1] = v;
  v2.color = color;

  remixapi_HardcodedVertex v3 {};
  v3.position[0] = p3[0]; v3.position[1] = p3[1]; v3.position[2] = p3[2];
  v3.normal[0] = normal[0]; v3.normal[1] = normal[1]; v3.normal[2] = normal[2];
  v3.texcoord[0] = u; v3.texcoord[1] = v;
  v3.color = color;

  vertices.push_back(v0);
  vertices.push_back(v1);
  vertices.push_back(v2);
  vertices.push_back(v3);

  indices.push_back(base + 0);
  indices.push_back(base + 1);
  indices.push_back(base + 2);
  indices.push_back(base + 0);
  indices.push_back(base + 2);
  indices.push_back(base + 3);
}

void appendRotatedBox(
    float ax, float az, float bx, float bz,
    float wx, float wz,
    float yMin, float yMax,
    std::uint32_t color,
    std::vector<remixapi_HardcodedVertex>& vertices,
    std::vector<std::uint32_t>& indices) {
  const float p0[3] = {ax - wx, yMin, az + wz};
  const float p1[3] = {ax + wx, yMin, az - wz};
  const float p2[3] = {bx + wx, yMin, bz - wz};
  const float p3[3] = {bx - wx, yMin, bz + wz};

  const float p4[3] = {ax - wx, yMax, az + wz};
  const float p5[3] = {ax + wx, yMax, az - wz};
  const float p6[3] = {bx + wx, yMax, bz - wz};
  const float p7[3] = {bx - wx, yMax, bz + wz};

  const float up[3] = {0.0f, 1.0f, 0.0f};
  const float down[3] = {0.0f, -1.0f, 0.0f};
  const float side0[3] = {-wz, 0.0f, wx};
  const float side1[3] = {wz, 0.0f, -wx};
  const float capA[3] = {ax - bx, 0.0f, az - bz};
  const float capB[3] = {bx - ax, 0.0f, bz - az};

  // Top
  appendQuad(p4, p5, p6, p7, up, color, vertices, indices);
  // Bottom
  appendQuad(p3, p2, p1, p0, down, color, vertices, indices);
  // Sides
  appendQuad(p0, p3, p7, p4, side0, color, vertices, indices);
  appendQuad(p2, p1, p5, p6, side1, color, vertices, indices);
  // Caps
  appendQuad(p1, p0, p4, p5, capA, color, vertices, indices);
  appendQuad(p3, p2, p6, p7, capB, color, vertices, indices);
}

void appendLightLevelXGeometry(
    float x0,
    float y0,
    float z0,
    std::uint32_t color,
    std::vector<remixapi_HardcodedVertex>& vertices,
    std::vector<std::uint32_t>& indices) {
  const float yMin = y0 + 0.02f;
  const float yMax = y0 + 0.05f;
  const float w = 0.035f;

  // Line 1: (x0+0.1, z0+0.1) to (x0+0.9, z0+0.9)
  appendRotatedBox(
      x0 + 0.1f, z0 + 0.1f,
      x0 + 0.9f, z0 + 0.9f,
      -w, w,
      yMin, yMax,
      color, vertices, indices);

  // Line 2: (x0+0.1, z0+0.9) to (x0+0.9, z0+0.1)
  appendRotatedBox(
      x0 + 0.1f, z0 + 0.9f,
      x0 + 0.9f, z0 + 0.1f,
      w, w,
      yMin, yMax,
      color, vertices, indices);
}

}  // namespace

void RemixRenderer::setLightLevelOverlayEnabled(bool enabled) {
  MCRTX_PERF_SCOPE(::mcrtx::perf::Side::Native, "RemixRenderer::setLightLevelOverlayEnabled");
  std::scoped_lock lock(mutex_);
  lightLevelOverlayEnabled_ = enabled;
  if (!enabled) {
    destroyLightLevelOverlayMesh();
    lightLevelMarkers_.clear();
  }
}

void RemixRenderer::clearLightLevelMarkers() {
  MCRTX_PERF_SCOPE(::mcrtx::perf::Side::Native, "RemixRenderer::clearLightLevelMarkers");
  std::scoped_lock lock(mutex_);
  lightLevelMarkers_.clear();
}

void RemixRenderer::submitLightLevelMarkers(const int* markerData, int count) {
  MCRTX_PERF_SCOPE(::mcrtx::perf::Side::Native, "RemixRenderer::submitLightLevelMarkers");
  std::scoped_lock lock(mutex_);
  lightLevelMarkers_.clear();
  if (markerData == nullptr || count <= 0) {
    return;
  }

  lightLevelMarkers_.reserve(static_cast<std::size_t>(count));
  for (int i = 0; i < count; ++i) {
    LightLevelMarkerInstance marker {};
    marker.blockX = markerData[i * 4 + 0];
    marker.blockY = markerData[i * 4 + 1];
    marker.blockZ = markerData[i * 4 + 2];
    marker.type = markerData[i * 4 + 3];
    lightLevelMarkers_.push_back(marker);
  }
}

void RemixRenderer::destroyLightLevelOverlayMesh() {
  MCRTX_PERF_SCOPE(::mcrtx::perf::Side::Native, "RemixRenderer::destroyLightLevelOverlayMesh");
  destroyMeshHandle(lightLevelOverlayMeshHandle_);
}

void RemixRenderer::createLightLevelOverlayMaterials() {
}

void RemixRenderer::destroyLightLevelOverlayMaterials() {
}

bool RemixRenderer::rebuildLightLevelOverlayMesh(const WorldRenderOrigin& renderOrigin) {
  MCRTX_PERF_SCOPE(::mcrtx::perf::Side::Native, "RemixRenderer::rebuildLightLevelOverlayMesh");
  MCRTX_TRACY_SCOPE("RemixRenderer::rebuildLightLevelOverlayMesh");
  if (!lightLevelOverlayEnabled_ || lightLevelMarkers_.empty()) {
    destroyLightLevelOverlayMesh();
    return true;
  }

  remixapi_MaterialHandle materialHandle = blockOutlineGlowMaterialHandle_ != nullptr
      ? blockOutlineGlowMaterialHandle_
      : destroyOverlayMaterialHandle_;
  if (materialHandle == nullptr) {
    destroyLightLevelOverlayMesh();
    return true;
  }

  std::vector<remixapi_HardcodedVertex> vertices;
  std::vector<std::uint32_t> indices;
  vertices.reserve(lightLevelMarkers_.size() * 48);
  indices.reserve(lightLevelMarkers_.size() * 72);

  const std::uint32_t redColor = packVertexColorRgba(1.0f, 0.12f, 0.12f, 1.0f);
  const std::uint32_t yellowColor = packVertexColorRgba(1.0f, 0.85f, 0.12f, 1.0f);

  for (const LightLevelMarkerInstance& marker : lightLevelMarkers_) {
    const WorldRenderPosition pos = rebaseWorldPosition(
        static_cast<float>(marker.blockX),
        static_cast<float>(marker.blockY),
        static_cast<float>(marker.blockZ),
        renderOrigin);

    const std::uint32_t color = (marker.type == 0) ? redColor : yellowColor;
    appendLightLevelXGeometry(pos.x, pos.y, pos.z, color, vertices, indices);
  }

  if (indices.empty()) {
    destroyLightLevelOverlayMesh();
    return true;
  }

  remixapi_MeshInfoSurfaceTriangles surface {};
  surface.vertices_values = vertices.data();
  surface.vertices_count = vertices.size();
  surface.indices_values = indices.data();
  surface.indices_count = indices.size();
  surface.skinning_hasvalue = FALSE;
  surface.material = materialHandle;

  remixapi_MeshInfo meshInfo {};
  meshInfo.sType = REMIXAPI_STRUCT_TYPE_MESH_INFO;
  meshInfo.hash = makeLightLevelOverlayMeshHash(nextLightLevelOverlayMeshHash_++);
  meshInfo.surfaces_values = &surface;
  meshInfo.surfaces_count = 1;

  remixapi_MeshHandle newMeshHandle = nullptr;
  const remixapi_ErrorCode result = [&]() {
    MCRTX_TRACY_SCOPE("rebuildLightLevelOverlayMesh.createMesh");
    MCRTX_PERF_SCOPE(::mcrtx::perf::Side::Remix, "CreateMesh.lightLevelOverlay");
    return remix_.CreateMesh(&meshInfo, &newMeshHandle);
  }();
  if (result != REMIXAPI_ERROR_CODE_SUCCESS) {
    setError("CreateMesh failed: " + errorCodeToString(result));
    destroyLightLevelOverlayMesh();
    return false;
  }

  destroyLightLevelOverlayMesh();
  lightLevelOverlayMeshHandle_ = newMeshHandle;
  lightLevelMarkerCount_ = lightLevelMarkers_.size();
  return true;
}

}  // namespace mcrtx
