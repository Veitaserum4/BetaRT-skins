#include "mcrtx/scene/remix_aerial_perspective.hpp"

#include <algorithm>
#include <iomanip>
#include <sstream>

namespace mcrtx {

namespace {

std::string formatFloat(float value, int precision) {
  std::ostringstream stream;
  stream << std::fixed << std::setprecision(precision) << value;
  return stream.str();
}

// Held off interior geometry. Remix resolves scene shadowing of the marched
// column on a 32x32 screen grid, so a surface just past the volume's near
// bound reads a column blended from neighbours up to ~60 px away; where those
// look past it into sunlit air the forward-scatter lobe lands on it as a halo
// through the wall. Six blocks clears the inside of any room Beta builds while
// still being close enough that a receding floor hazes.
constexpr float kNearFadeStartBlocks = 6.0f;

// Full strength by 24 blocks - a chunk and a half - so the ramp is spent well
// inside even the Tiny render distance and no band shows where it takes over.
constexpr float kNearFadeEndBlocks = 24.0f;

// The depth axis distributes its 32 slices exponentially, so over-reaching the
// far bound costs near-field accuracy only logarithmically. Four times the
// render distance leaves headroom for geometry beyond the fog without spending
// half the volume on empty distance.
constexpr float kDepthRangeViewMultiplier = 4.0f;

// Beta's Tiny render distance is 32 blocks. Guard against a farPlane arriving
// before the camera is real.
constexpr float kMinViewDistanceBlocks = 32.0f;
constexpr float kMaxViewDistanceBlocks = 512.0f;

// Past this the shadow rays are pure cost: nothing in a Minecraft world is a
// mountain ridge, and the air above the treeline is sunlit anyway.
constexpr float kMaxSceneShadowBlocks = 128.0f;

// rtx.atmosphere.aerialPerspectiveDepthRangeMeters has a floor of 100.
constexpr float kMinDepthRangeMeters = 100.0f;

}  // namespace

float aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength strength) {
  switch (strength) {
    case AerialPerspectiveStrength::Subtle:
      return 2.0f;
    case AerialPerspectiveStrength::Strong:
      return 10.0f;
    case AerialPerspectiveStrength::Extreme:
      return 20.0f;
    case AerialPerspectiveStrength::Normal:
    default:
      return 5.0f;
  }
}

AerialPerspectiveStrength aerialPerspectiveStrengthFromInt(int strength) {
  switch (strength) {
    case 0:
      return AerialPerspectiveStrength::Subtle;
    case 2:
      return AerialPerspectiveStrength::Strong;
    case 3:
      return AerialPerspectiveStrength::Extreme;
    case 1:
    default:
      return AerialPerspectiveStrength::Normal;
  }
}

AerialPerspectiveConfigValues aerialPerspectiveConfigValues(
    bool enabled,
    AerialPerspectiveStrength strength,
    float viewDistanceBlocks,
    bool sceneShadowEnabled) {
  const float metersPerBlock = aerialPerspectiveMetersPerBlock(strength);

  // rtx.atmosphere.aerialPerspectiveScale is game units per centimetre, and
  // BetaRT submits one block per game unit, so this is the inverse of the
  // metres a block stands for divided across a metre's hundred centimetres.
  const float scale = 1.0f / (100.0f * metersPerBlock);

  const float viewBlocks =
      std::clamp(viewDistanceBlocks, kMinViewDistanceBlocks, kMaxViewDistanceBlocks);

  // Every distance below is authored in blocks and multiplied into metres by
  // the same figure the scale was derived from, which is what keeps the fade
  // and shadow bounds fixed in the world as the strength changes.
  const float depthRangeMeters =
      std::max(viewBlocks * kDepthRangeViewMultiplier * metersPerBlock, kMinDepthRangeMeters);
  const float nearFadeStartMeters = kNearFadeStartBlocks * metersPerBlock;
  const float nearFadeEndMeters = kNearFadeEndBlocks * metersPerBlock;
  const float sceneShadowRangeMeters =
      std::min(viewBlocks, kMaxSceneShadowBlocks) * metersPerBlock;

  return {{
      {"rtx.atmosphere.aerialPerspective", enabled ? "True" : "False"},
      {"rtx.atmosphere.aerialPerspectiveScale", formatFloat(scale, 6)},
      {"rtx.atmosphere.aerialPerspectiveDepthRangeMeters", formatFloat(depthRangeMeters, 2)},
      {"rtx.atmosphere.aerialPerspectiveNearFadeStartMeters", formatFloat(nearFadeStartMeters, 2)},
      {"rtx.atmosphere.aerialPerspectiveNearFadeEndMeters", formatFloat(nearFadeEndMeters, 2)},
      {"rtx.atmosphere.aerialPerspectiveSceneShadow", sceneShadowEnabled ? "True" : "False"},
      {"rtx.atmosphere.aerialPerspectiveSceneShadowRangeMeters",
       formatFloat(sceneShadowRangeMeters, 2)},
  }};
}

}  // namespace mcrtx
