# Pong3D

A 3D Pong game built in Java using JOGL (Java OpenGL), featuring physically-based rendering (PBR), toon shading, a noise shader, real-time shadows, and power-ups.

---

## Table of Contents

1. [Architecture & Entry Point](#1-architecture--entry-point)
2. [Rendering Pipeline & OpenGL Setup](#2-rendering-pipeline--opengl-setup)
3. [Game Loop Structure](#3-game-loop-structure)
4. [Scene Objects, Math & Camera](#4-scene-objects-math--camera)
5. [Input Handling & Controls](#5-input-handling--controls)
6. [Collision Detection & Scoring](#6-collision-detection--scoring)
7. [Shader Organization & Loading](#7-shader-organization--loading)
8. [VBO File Format & Asset Loading](#8-vbo-file-format--asset-loading)
9. [Build & Run Instructions](#9-build--run-instructions)
10. [Notable Patterns & Utilities](#10-notable-patterns--utilities)

---

## 1. Architecture & Entry Point

The project is a single-module IntelliJ IDEA Java project (`Pong3D.iml`). All game code lives in one file:

| File | Role |
|------|------|
| `src/Pong3DWithShaders.java` | **Main game** — all ~1,131 lines of game logic, rendering, and shaders |
| `src/Main.java` | IntelliJ boilerplate stub — unused |
| `src/Texture.java` | Early rotating-textured-cube demo — unused by the game |
| `src/texture.vert` / `src/texture.frag` | Standalone GLSL files for the cube demo — not loaded by the game |

The true entry point is `Pong3DWithShaders.main()`, which calls `SwingUtilities.invokeLater` and creates a `MyGui` window.

---

## 2. Rendering Pipeline & OpenGL Setup

`MyGui` extends `JFrame` and implements JOGL's `GLEventListener`:

- **Context creation**: `GLProfile.getDefault()` + `GLCapabilities` → `GLCanvas`. A comment shows how to switch to `GLProfile.GL4` for ARM Macs.
- **Animator**: `FPSAnimator` targeting 120 FPS.
- **`init()`**: Requests an OpenGL 3 context, enables `GL_DEPTH_TEST`, and builds a perspective projection matrix (60° FOV, 16:9 aspect, near=1.5, far=5.5).
- **`reshape()`**: Maintains a letterboxed 16:9 viewport by computing `glViewport` offsets.
- **`display()`**: Calls `game.update()` then `game.display()` each frame.

`Game.display()` renders each game object **twice**:
1. **Normal pass** (`shadowMode = false`) — object at Z = -2.0 with full XYZ scale.
2. **Shadow pass** (`shadowMode = true`) — object flattened (Z scale = 0) at Z = -2.25, rendered in dark grey (`vec4(0.1, 0.1, 0.1, 1.0)`), projecting a fake planar shadow onto the court.

The court itself is skipped on the shadow pass.

Vertex attributes are set per-draw-call via `glVertexAttribPointer` (no VAOs).

---

## 3. Game Loop Structure

```
FPSAnimator (120 Hz) → MyGui.display()
    game.update()   ← physics, collision, power-up spawning
    game.display()  ← OpenGL rendering (update + shadow passes)
```

`game.update()` each frame:
1. Optionally tracks ball position with the light source.
2. Calls `update()` on every `GameObject` (moves paddles, ball, power-up).
3. Increments continuous rotation angles (`rotationY`, `rotationZ`).
4. Runs three collision checks.
5. Has a 0.25% per-frame chance to spawn a power-up.

Physics is **fixed-step** (no delta-time), so speed scales with frame rate.

---

## 4. Scene Objects, Math & Camera

All objects inherit from `GameObject`, which holds position (`posX`, `posY`), scale (`sizeX/Y/Z`), rotation angles, a VBO handle, and a texture ID.

| Class | Description |
|-------|-------------|
| `Player` | Paddle with acceleration-based movement and AABB collision borders |
| `Ball` | Moves by constant velocity each frame; resets on score |
| `Court` | Slowly rotates around Y (`rotationY = -0.01f`), size = 2.0 |
| `Score` | Swaps active VBO to a pre-baked digit mesh (0–3) |
| `PowerUp` | Bounces vertically; three types: grow, shrink, speed boost |

**Player movement** uses a simple acceleration + damping model:
```
acceleration = ACCELERATION_VALUE (0.012f)  // when key held
velocity += acceleration
velocity *= 0.75f  // damping
posY += velocity
posY = clamp(posY, -0.8f, 0.8f)
```

**Camera / transforms**: There is no explicit camera. All objects are placed at a fixed Z of -2.0 in view space. The perspective projection matrix is set once at startup. The `modelview` matrix is built per-object each frame as translate → scale → Euler rotate using JOGL's `Matrix4f`. The normal matrix is computed as `transpose(inverse(modelview))`.

---

## 5. Input Handling & Controls

`Game` extends `java.awt.event.KeyAdapter`. `keyPressed` sets boolean flags; `keyReleased` clears them.

| Key | Action |
|-----|--------|
| **W** / **S** | Player 1 move up / down |
| **P** / **L** | Player 2 move up / down |
| **SPACE** | Start / resume game |
| **0** | Light from front (default) |
| **1** | Light from below |
| **2** | Light from above |
| **3** | Light from diagonal |
| **4** | Light follows ball |
| **5** / **6** | Metallic = 0.0 / 1.0 |
| **7** / **8** | Roughness = 0.1 / 0.2 |
| **9** | Cycle shading: PBR → Toon → Noise |

---

## 6. Collision Detection & Scoring

All collision is **AABB (axis-aligned bounding box)**.

**Ball vs Paddle**: Checks overlap on both X and Y axes. On hit:
- Ball is repositioned to avoid tunneling (offset weighted by distance-to-paddle-center).
- Spin is applied: `ball.rotationZ = player.velocity * 273`.
- Velocity is reflected and spin adds a Y component: `velocityY += rotationZ * 0.0015f`.

**Ball vs Border**:
- `posX > 1.9` → Player 1 scores; `posX < -1.9` → Player 2 scores.
- `|posY| > 1.0` → Y velocity reverses (ceiling/floor bounce).

**Ball vs PowerUp**: AABB overlap using `sizeX + ball.sizeX` margin. The direction of `velocityX` determines which player is the "consumer" (beneficiary). A `java.util.Timer` fires after 4,000 ms to revert the power-up effect.

**Power-up types**:

| Type | Icon | Effect (on consumer) |
|------|------|----------------------|
| 0 | `powerup_icons_grow.png` | Double paddle height |
| 1 | `powerup_icons_shrink.png` | Halve opponent's paddle height |
| 2 | `powerup_icons_star.png` | Double acceleration |

**Scoring**: First to score > 2 points wins. On score, ball resets and game pauses until SPACE is pressed. If either player has > 2 points when SPACE is pressed, scores reset.

---

## 7. Shader Organization & Loading

Shaders are **inlined as Java 15+ text blocks** inside the `Shader` class — no external `.glsl` files are loaded at runtime.

### Vertex Shader (GLSL `#version 140`)

Inputs: `inputPosition (vec3)`, `inputColor (vec4)`, `inputTexCoord (vec2)`, `inputNormal (vec3)`  
Uniforms: `projection (mat4)`, `modelview (mat4)`, `normalMat (mat4)`  
Outputs: `normal`, `vertPos`, `forFragColor`, `forFragTexCoord`

### Fragment Shader (GLSL `#version 140`)

A single shader with three selectable modes via `uniform int shading`:

| `shading` | Mode | Description |
|-----------|------|-------------|
| 0 | **PBR** (default) | Cook-Torrance microfacet: GGX normal distribution, Smith visibility, Schlick Fresnel. Controlled by `metallic` and `roughness` uniforms. |
| 1 | **Toon** | Step-function specular highlight using `smoothstep(0.9, 0.99, ...)`. |
| 2 | **Noise** | UV hash noise multiplied onto base color. |
| — | **Shadow** | Flat `vec4(0.1, 0.1, 0.1, 1.0)` when `uniform bool shadow` is true. |

All paths apply gamma correction (encode/decode with `pow(x, 2.2)`).

### Shader Compilation

Standard GL3 pipeline: `glCreateShader` → `glShaderSource` → `glCompileShader` → `glCreateProgram` → `glAttachShader` → `glLinkProgram`. All attribute and uniform locations are queried and cached in the `Shader` class.

### Standalone GLSL Files (unused by game)

`src/texture.vert` and `src/texture.frag` belong to `Texture.java` (the cube demo). `texture.frag` contains a Phong BRDF with a reflection mask.

---

## 8. VBO File Format & Asset Loading

### VBO Files (`VboLoader`)

Plain-text files with one float per line:
- **Line 1**: total float count
- **Remaining lines**: float values

Each vertex is **12 floats**: `3 (position) + 4 (color) + 2 (texcoord) + 3 (normal)`.  
Stride = `12 × sizeof(float)` = 48 bytes.

| File | Vertices | Description |
|------|----------|-------------|
| `ball.vbo` | 240 | Ball sphere |
| `player.vbo` | 6,624 | Paddle mesh (shared by both players) |
| `court.vbo` | 2,304 | Court/arena |
| `0.vbo`–`3.vbo` | ~96 each | Pre-baked digit meshes for score display |
| `box_tri.vbo` | — | Power-up icon quad |
| `bar.vbo`, `box.vbo`, `skybox.vbo`, `skybox_tri.vbo` | — | Unused/experimental meshes |

VBOs are uploaded with `glBufferData(GL_STATIC_DRAW)`.

### Textures (`TextureLoader`)

PNG files read via `javax.imageio.ImageIO`, vertically flipped (OpenGL origin is bottom-left), packed into a direct `ByteBuffer`, and uploaded with `glTexImage2D`. Filtering: bilinear (`GL_LINEAR`). No mipmaps.

| File | Used by |
|------|---------|
| `src/interstellar.png` | Court background |
| `src/white.png` | Ball, paddles, score digits |
| `src/powerup_icons_grow.png` | Power-up type 0 |
| `src/powerup_icons_shrink.png` | Power-up type 1 |
| `src/powerup_icons_star.png` | Power-up type 2 |
| `textures/interstellar.png` | Texture.java cube demo |
| `textures/powerup_texture.png` | Unused |

---

## 9. Build & Run Instructions

The project has **no Gradle, Maven, or build script**. It is an IntelliJ IDEA module.

### Prerequisites

- Java 15+ (for text block syntax)
- [JOGL 2](https://jogamp.org/jogl/www/) JARs placed in `~/Downloads/jogl/`:
  - `jogl-all.jar`
  - `gluegen-rt.jar`
  - `jogl-all-android-natives-windows-amd64.jar` (Windows x64 native)
  - `gluegen-rt-android-natives-windows-amd64.jar` (Windows x64 native)

### Steps

1. Clone the repository and open `Pong3D.iml` in IntelliJ IDEA.
2. Ensure the JOGL JARs are in `~/Downloads/jogl/` (or update the library paths in `.idea/libraries/`).
3. Set the **run configuration** to execute `Pong3DWithShaders` (not `Main` or `Texture`).
4. Run the project. The window opens at 800×450 (16:9), press **SPACE** to start.

> **macOS ARM note**: If OpenGL 3 is unsupported, change `GLProfile.getDefault()` to `GLProfile.get(GLProfile.GL4)` on line 42 of `Pong3DWithShaders.java`.

---

## 10. Notable Patterns & Utilities

- **Monolithic file**: All 12 classes in a single ~1,131-line file — no packages, no separation of concerns.
- **Inline GLSL**: Shaders are Java text blocks, not external `.glsl` files. The `src/texture.vert` and `src/texture.frag` files are orphaned.
- **No VAOs**: `glVertexAttribPointer` is called every draw call inside `renderGameObject()` — a legacy pattern despite requesting GL3.
- **Planar shadow via scale-to-zero**: Each non-court object is drawn a second time with Z scale = 0 to fake a floor shadow, rather than using shadow maps or stencil volumes.
- **Fixed-step physics**: All velocity/position updates are done once per rendered frame with no delta-time, so game speed depends on frame rate.
- **Score as mesh**: Digits 0–3 are each a pre-baked VBO mesh; `Score.setScore()` simply swaps the active buffer — no font rendering.
- **Power-up timer race**: `java.util.Timer` runs on a background thread to revert power-up effects; no synchronization with the OpenGL render thread.
- **`Util` class**: A single shared `static Random rand` instance used throughout for power-up randomization.
