#version 140

#define RECIPROCAL_PI 0.3183098861837907

// Output to the framebuffer
out vec4 outputColor;

// Inputs from vertex shader
in vec2 forFragTexCoord;
in vec3 normal;
in vec3 vertPos;
in vec3 forFragColor;

// Uniforms
uniform sampler2D myTexture;    // Texture sampler
uniform vec3 lightDirection;    // Direction of the light source
uniform bool shading;           // Toggle for shading
uniform float metallic;         // Metallic factor
uniform float roughness;        // Roughness factor

// Constants
const vec4 lightColor = vec4(1.0, 1.0, 1.0, 1.0); // White light color
const float irradiPerp = 1.0;                    // Perpendicular irradiance

// Function to convert sRGB to linear space
vec3 rgb2lin(vec3 rgb) {
    return pow(rgb, vec3(2.2));
}

// Fresnel-Schlick approximation
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

// GGX normal distribution function
float D_GGX(float NoH, float roughness) {
    float alpha = roughness * roughness;
    float alpha2 = alpha * alpha;
    float NoH2 = NoH * NoH;
    float b = (NoH2 * (alpha2 - 1.0) + 1.0);
    return alpha2 * RECIPROCAL_PI / (b * b);
}

// Geometry function (single direction) using Schlick-GGX
float G1_GGX_Schlick(float NoV, float roughness) {
    float r = 0.5 + 0.5 * roughness; // Disney remapping
    float k = (r * r) / 2.0;
    float denom = NoV * (1.0 - k) + k;
    return max(NoV, 0.001) / denom;
}

// Combined geometry function using Smith's method
float G_Smith(float NoV, float NoL, float roughness) {
    float g1_l = G1_GGX_Schlick(NoL, roughness);
    float g1_v = G1_GGX_Schlick(NoV, roughness);
    return g1_l * g1_v;
}

// Microfacet-based BRDF
vec3 brdfMicrofacet(
    in vec3 L,
    in vec3 V,
    in vec3 N,
    in float metallic,
    in float roughness,
    in vec3 baseColor,
    in float reflectance
) {
    vec3 H = normalize(V + L); // Half vector

    // Dot products
    float NoV = clamp(dot(N, V), 0.0, 1.0);
    float NoL = clamp(dot(N, L), 0.0, 1.0);
    float NoH = clamp(dot(N, H), 0.0, 1.0);
    float VoH = clamp(dot(V, H), 0.0, 1.0);

    // Fresnel-Schlick approximation
    vec3 f0 = vec3(0.16 * (reflectance * reflectance));
    f0 = mix(f0, baseColor, metallic); // Metallic adjustment
    vec3 F = fresnelSchlick(VoH, f0);

    // GGX normal distribution function
    float D = D_GGX(NoH, roughness);

    // Geometry function
    float G = G_Smith(NoV, NoL, roughness);

    // Specular BRDF
    vec3 spec = (F * D * G) / (4.0 * max(NoV, 0.001) * max(NoL, 0.001));

    // Diffuse BRDF
    vec3 rhoD = baseColor * (1.0 - metallic) * (1.0 - F); // Non-metallic diffuse
    vec3 diff = rhoD * RECIPROCAL_PI;

    return diff + spec;
}

void main() {
    if (shading) {
        // Normalize inputs
        vec3 n = normalize(normal);
        vec3 lightDir = normalize(-lightDirection);
        vec3 viewDir = normalize(-vertPos);

        // Texture and base color
        vec3 baseColor = rgb2lin(forFragColor * texture(myTexture, forFragTexCoord).rgb);

        // Reflectance factor
        float reflectance = 0.5;

        // Initial radiance
        vec3 radiance = vec3(0.0);

        // Light contribution
        float irradiance = max(dot(lightDir, n), 0.0) * irradiPerp;
        if (irradiance > 0.0) {
            vec3 brdf = brdfMicrofacet(lightDir, viewDir, n, metallic, roughness, baseColor, reflectance);
            radiance += brdf * irradiance * lightColor.rgb;
        }

        // Gamma correction
        radiance = pow(radiance, vec3(1.0 / 2.2));

        // Final output color
        outputColor = vec4(radiance, 1.0);
    } else {
        // No shading
        vec3 textureColor = forFragColor * texture(myTexture, forFragTexCoord).rgb;
        outputColor = vec4(textureColor, 1.0);
    }
}
