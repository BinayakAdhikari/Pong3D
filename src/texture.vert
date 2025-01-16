#version 140

// Inputs from the application (vertex attributes)
in vec3 inputPosition;   // Vertex position
in vec4 inputColor;      // Vertex color
in vec2 inputTexCoord;   // Texture coordinates
in vec3 inputNormal;     // Vertex normal

// Uniform matrices
uniform mat4 projection; // Projection matrix
uniform mat4 modelview;  // Model-view matrix
uniform mat4 normalMat;  // Normal transformation matrix (inverse-transpose of modelview)

// Outputs to the fragment shader
out vec3 forFragColor;    // Interpolated color
out vec2 forFragTexCoord; // Interpolated texture coordinates
out vec3 normal;          // Transformed normal vector
out vec3 vertPos;         // Vertex position in world space

void main() {
    // Pass color and texture coordinates to fragment shader
    forFragColor = inputColor.rgb;
    forFragTexCoord = inputTexCoord;

    // Transform the normal vector
    normal = normalize((normalMat * vec4(inputNormal, 0.0)).xyz);

    // Transform the vertex position
    vec4 vertPos4 = modelview * vec4(inputPosition, 1.0);
    vertPos = vec3(vertPos4) / vertPos4.w;

    // Final clip-space position
    gl_Position = projection * vertPos4;
}
