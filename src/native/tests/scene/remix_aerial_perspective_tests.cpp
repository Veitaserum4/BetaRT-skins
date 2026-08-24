#include "mcrtx/scene/remix_aerial_perspective.hpp"

#include <cmath>
#include <cstdlib>
#include <iostream>
#include <string>
#include <string_view>

namespace {

void require(bool condition, const char* message) {
  if (!condition) {
    std::cerr << message << '\n';
    std::exit(1);
  }
}

void requireValue(std::string_view actual, std::string_view expected, const char* message) {
  if (actual != expected) {
    std::cerr << message << ": expected " << expected << ", got " << actual << '\n';
    std::exit(1);
  }
}

void requireNear(float actual, float expected, float tolerance, const char* message) {
  if (std::fabs(actual - expected) > tolerance) {
    std::cerr << message << ": expected " << expected << ", got " << actual << '\n';
    std::exit(1);
  }
}

float valueOf(const mcrtx::AerialPerspectiveConfigValues& values, std::string_view key) {
  for (const mcrtx::AerialPerspectiveConfigEntry& entry : values) {
    if (entry.key == key) {
      return std::stof(entry.value);
    }
  }
  std::cerr << "missing key: " << key << '\n';
  std::exit(1);
}

}  // namespace

int main() {
  using mcrtx::AerialPerspectiveStrength;

  // Key set and order are part of the contract the renderer pushes.
  const auto normal = mcrtx::aerialPerspectiveConfigValues(
      true, AerialPerspectiveStrength::Normal, 256.0f, true);
  require(normal.size() == 7, "config count");
  requireValue(normal[0].key, "rtx.atmosphere.aerialPerspective", "enable key");
  requireValue(normal[0].value, "True", "enable value");
  requireValue(normal[5].key, "rtx.atmosphere.aerialPerspectiveSceneShadow", "shadow key");
  requireValue(normal[5].value, "True", "shadow value");

  const auto disabled = mcrtx::aerialPerspectiveConfigValues(
      false, AerialPerspectiveStrength::Normal, 256.0f, false);
  requireValue(disabled[0].value, "False", "disabled enable value");
  requireValue(disabled[5].value, "False", "disabled shadow value");

  // rtx.atmosphere.aerialPerspectiveScale is game units per centimetre, and one
  // game unit is one block, so it must be the reciprocal of the metres a block
  // stands for spread over a metre's hundred centimetres. Getting this backwards
  // is the difference between five times the haze and a fifth of it.
  for (const AerialPerspectiveStrength strength :
       {AerialPerspectiveStrength::Subtle,
        AerialPerspectiveStrength::Normal,
        AerialPerspectiveStrength::Strong,
        AerialPerspectiveStrength::Extreme}) {
    const float metersPerBlock = mcrtx::aerialPerspectiveMetersPerBlock(strength);
    const auto values = mcrtx::aerialPerspectiveConfigValues(true, strength, 256.0f, true);
    requireNear(
        valueOf(values, "rtx.atmosphere.aerialPerspectiveScale"),
        1.0f / (100.0f * metersPerBlock),
        1e-6f,
        "scale is the reciprocal of metres per block over 100");

    // Every distance is authored in blocks, so dividing back out by the same
    // figure must land on a block count that does not move with strength.
    requireNear(
        valueOf(values, "rtx.atmosphere.aerialPerspectiveNearFadeStartMeters") / metersPerBlock,
        6.0f,
        0.01f,
        "near fade start stays 6 blocks");
    requireNear(
        valueOf(values, "rtx.atmosphere.aerialPerspectiveNearFadeEndMeters") / metersPerBlock,
        24.0f,
        0.01f,
        "near fade end stays 24 blocks");
    requireNear(
        valueOf(values, "rtx.atmosphere.aerialPerspectiveSceneShadowRangeMeters") / metersPerBlock,
        128.0f,
        0.01f,
        "shadow range clamps to 128 blocks");
  }

  // Strength is monotonic in how much atmosphere a block stands for.
  require(
      mcrtx::aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength::Subtle)
          < mcrtx::aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength::Normal),
      "subtle < normal");
  require(
      mcrtx::aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength::Normal)
          < mcrtx::aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength::Strong),
      "normal < strong");
  require(
      mcrtx::aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength::Strong)
          < mcrtx::aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength::Extreme),
      "strong < extreme");

  // Depth range tracks the render distance so the exponential slices are not
  // spent past anything the game draws.
  const auto far = mcrtx::aerialPerspectiveConfigValues(
      true, AerialPerspectiveStrength::Normal, 256.0f, true);
  const auto tiny = mcrtx::aerialPerspectiveConfigValues(
      true, AerialPerspectiveStrength::Normal, 32.0f, true);
  require(
      valueOf(far, "rtx.atmosphere.aerialPerspectiveDepthRangeMeters")
          > valueOf(tiny, "rtx.atmosphere.aerialPerspectiveDepthRangeMeters"),
      "depth range grows with render distance");

  // rtx.atmosphere.aerialPerspectiveDepthRangeMeters has a runtime floor of 100.
  const auto degenerate = mcrtx::aerialPerspectiveConfigValues(
      true, AerialPerspectiveStrength::Subtle, 0.0f, true);
  require(
      valueOf(degenerate, "rtx.atmosphere.aerialPerspectiveDepthRangeMeters") >= 100.0f,
      "depth range respects the runtime floor");
  // A far plane arriving before the camera is real must not collapse the volume.
  requireNear(
      valueOf(degenerate, "rtx.atmosphere.aerialPerspectiveSceneShadowRangeMeters"),
      32.0f * mcrtx::aerialPerspectiveMetersPerBlock(AerialPerspectiveStrength::Subtle),
      0.01f,
      "view distance clamps up to the Tiny render distance");

  // Out-of-range ints from the settings layer fall back to Normal.
  require(
      mcrtx::aerialPerspectiveStrengthFromInt(-1) == AerialPerspectiveStrength::Normal,
      "negative strength falls back");
  require(
      mcrtx::aerialPerspectiveStrengthFromInt(99) == AerialPerspectiveStrength::Normal,
      "oversized strength falls back");
  require(
      mcrtx::aerialPerspectiveStrengthFromInt(3) == AerialPerspectiveStrength::Extreme,
      "in-range strength maps through");

  return 0;
}
