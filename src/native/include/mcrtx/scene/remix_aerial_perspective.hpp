#pragma once

#include <array>
#include <string>
#include <string_view>

namespace mcrtx {

// Aerial perspective is the atmosphere's in-scatter and extinction applied to
// scene geometry through a camera-fitted froxel volume. Remix bakes and
// composites it whenever rtx.skyMode is Numos, which BetaRT always selects, so
// the feature is not switched on here so much as calibrated.
//
// The calibration is the whole problem. Remix's defaults - a 50 m to 250 m
// near fade over a 32 km depth range - come from Hillaire EGSR 2020 and are
// sized for a 3 km world. Minecraft submits 1 block as 1 metre and Beta 1.7.3
// draws at most 256 blocks, so on those defaults the haze has only just begun
// to ramp in by the time it reaches the far plane, and clear air over 256 m
// removes very little light regardless. The result reads as "aerial
// perspective does nothing".
//
// rtx.atmosphere.aerialPerspectiveScale exists for exactly this case. It
// overrides rtx.sceneScale for the aerial perspective volume alone, leaving
// the sky, the clouds and the global volumetrics on the real scale, which lets
// the world be told to the atmosphere as bigger than it is. Every distance
// knob below is then expressed in blocks and converted through the same
// figure, so the fade and shadow bounds stay put in the world while the amount
// of atmosphere between the camera and a surface scales with the strength.
enum class AerialPerspectiveStrength : int {
  Subtle = 0,
  Normal = 1,
  Strong = 2,
  Extreme = 3,
};

struct AerialPerspectiveConfigEntry {
  std::string_view key;
  std::string value;
};

// rtx.atmosphere.aerialPerspective plus the six knobs that calibrate it.
using AerialPerspectiveConfigValues = std::array<AerialPerspectiveConfigEntry, 7>;

// Metres of atmosphere one block stands for at the given strength. This is the
// exaggeration factor; 1.0 would be physically honest and visually inert.
float aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength strength);

// Clamps an arbitrary int (from the Java settings layer) onto the enum.
AerialPerspectiveStrength aerialPerspectiveStrengthFromInt(int strength);

// viewDistanceBlocks is the game's render distance in blocks. Remix's camera
// farPlane is twice that (Minecraft passes farPlaneDistance and projects to
// farPlaneDistance * 2), so callers working from the camera should halve it.
AerialPerspectiveConfigValues aerialPerspectiveConfigValues(
    bool enabled,
    AerialPerspectiveStrength strength,
    float viewDistanceBlocks,
    bool sceneShadowEnabled);

}  // namespace mcrtx
