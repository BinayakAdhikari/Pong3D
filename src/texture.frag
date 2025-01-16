#version 140
out vec4 outputColor;

in vec2 forFragTexCoord;
in vec3 normal;
in vec3 vertPos;
in vec3 forFragColor;

uniform sampler2D myTexture;
uniform vec3 lightDirection;
uniform bool shading;

// Constants
const vec4 lightColor = vec4(1.0, 1.0, 1.0, 1.0);
const vec3 ambientLight = vec3(0.05, 0.05, 0.05); // Reduced ambient for background
const vec3 specularColor = vec3(0.3, 0.3, 0.3);
const float shininess = 20.0;
const float irradiPerp = 1.0;

vec3 phongBRDF(
in vec3 lightDir,
in vec3 viewDir,
in vec3 normal,
in vec3 phongDiffuseCol,
in vec3 phongSpecularCol,
float phongShininess
) {
    vec3 color = phongDiffuseCol;
    vec3 reflectDir = reflect(-lightDir, normal);
    float specDot = max(dot(reflectDir, viewDir), 0.0);
    color += pow(specDot, phongShininess) * phongSpecularCol;
    return color;
}

void main() {
    if (shading) {
        vec3 n = normalize(normal);
        vec3 lightDir = normalize(-lightDirection);
        vec3 viewDir = normalize(-vertPos);

        // Fetch the texture color
        vec3 textureColor = texture(myTexture, forFragTexCoord).rgb;

        // Darken background: Adjust contribution based on texture color intensity
        float reflectionMask = length(textureColor); // Reflective areas are brighter
        reflectionMask = smoothstep(0.2, 0.8, reflectionMask); // Threshold adjustment

        // Apply gamma correction to diffuse color
        vec3 diffuseColor = forFragColor * textureColor;
        diffuseColor = pow(diffuseColor, vec3(2.2));

        // Calculate ambient light (scaled by reflectionMask)
        vec3 radiance = ambientLight * diffuseColor * reflectionMask;

        // Calculate diffuse and specular light
        float irradiance = max(dot(lightDir, n), 0.0) * irradiPerp;
        if (irradiance > 0.0) {
            vec3 brdf = phongBRDF(lightDir, viewDir, n, diffuseColor, specularColor, shininess);
            radiance += brdf * irradiance * lightColor.rgb * reflectionMask;
        }

        // Gamma correction for the final color
        radiance = pow(radiance, vec3(1.0 / 2.2));

        outputColor = vec4(radiance, 1.0);
    } else {
        // No shading
        vec3 textureColor = forFragColor * texture(myTexture, forFragTexCoord).rgb;
        outputColor = vec4(textureColor, 1.0);
    }
}
