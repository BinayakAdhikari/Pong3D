import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.glu.GLU;

public class Pong3D {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MyGui myGUI = new MyGui();
                myGUI.createGUI();
            }
        });
    }
}

class MyGui extends JFrame implements GLEventListener {
    private Game game;
    private final GLU glu = new GLU();

    public void createGUI() {
        setTitle("Pong3D");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GLProfile glp = GLProfile.getDefault();
        GLCapabilities caps = new GLCapabilities(glp);
        GLCanvas canvas = new GLCanvas(caps);
        final FPSAnimator ani = new FPSAnimator(canvas, 120, true);
        canvas.addGLEventListener(this);
        game = new Game();
        canvas.addKeyListener(game);
        ani.start();

        getContentPane().setPreferredSize(new Dimension(800, 450));
        getContentPane().add(canvas);
        pack();
        setVisible(true);
        canvas.requestFocus();
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2(); // get the OpenGL 2 graphics context
        // enable depth test
        gl.glEnable(gl.GL_DEPTH_TEST);

        // setup camera
        float aspect = 16.0f / 9.0f;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(60.0, aspect, 1.5f, 5.0f);

        game.init(d);
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int width, int height) {
        GL2 gl = d.getGL().getGL2(); // get the OpenGL 2 graphics context
        gl.glViewport(0, 0, width, height);
    }

    @Override
    public void display(GLAutoDrawable d) {
        game.update();
        game.display(d);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
    }
}

class Game extends KeyAdapter {
    boolean pauseGame = true;

    // gameobjects
    Player playerOne;
    Score scoreOne;
    Player playerTwo;
    Score scoreTwo;
    Ball ball;
    Court court;

    ArrayList<GameObject> gameObjects = new ArrayList<>();

    public Game() {
        // Instantiate game elements
        ball = new Ball();
        playerOne = new Player(-1.8f, 0f);
        scoreOne = new Score(-0.15f, 0.85f);
        playerTwo = new Player(1.8f, 0f);
        scoreTwo = new Score(0.15f, 0.85f);
        court = new Court();

        // populate gameobject list
        gameObjects.add(court);
        gameObjects.add(ball);
        gameObjects.add(playerOne);
        gameObjects.add(playerTwo);
        gameObjects.add(scoreOne);
        gameObjects.add(scoreTwo);
    }

    public void init(GLAutoDrawable d) {
        court.init(d);
    }

    public void display(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2(); // get the OpenGL 2 graphics context

        // clear the screen
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        for (GameObject gameObject : gameObjects) {
            gameObject.display(gl);
        }
        gl.glFlush();
    }

    public void update() {
        for (GameObject gameObject : gameObjects) {
            gameObject.update();
        }
        checkBallCollisionPlayer();
        checkBallCollisionBorder();
    }

    public void startGame() {
        if (scoreOne.getScore() > 2 || scoreTwo.getScore() > 2) {
            scoreOne.setScore(0);
            scoreTwo.setScore(0);
        }
        ball.velocityX = 0.03f;
        ball.velocityY = 0.015f;
        pauseGame = false;
    }

    public void score(Score score) {
        score.setScore(score.getScore() + 1);
        ball.reset();
        pauseGame = true;
    }

    public void checkBallCollisionPlayer() {
        // collision player one
        if (ball.borderLeft < playerOne.borderRight) {
            if (ball.borderDown < playerOne.borderUp && ball.borderUp > playerOne.borderDown) {
                ball.posX = playerOne.borderRight + ball.sizeX;

                // rotate ball
                ball.rotation = playerOne.velocity * 273;
                // reflect ball
                ball.velocityX = -(ball.velocityX + (ball.rotation * .0005f));
                ball.velocityY += (ball.rotation * .0015f);
            }
        }

        // collision player two
        if (ball.borderRight > playerTwo.borderLeft) {
            if (ball.borderDown < playerTwo.borderUp && ball.borderUp > playerTwo.borderDown) {
                ball.posX = playerTwo.borderLeft - ball.sizeX;

                // rotate ball
                ball.rotation = playerTwo.velocity * 273;
                // reflect ball
                ball.velocityX = -ball.velocityX + (ball.rotation * .0005f);
                ball.velocityY += (ball.rotation * .0015f);
            }
        }
    }

    public void checkBallCollisionBorder() {
        // let and right border
        if (ball.posX > 1.9f) {
            score(scoreOne);
        }
        if (ball.posX < -1.9f) {
            score(scoreTwo);
        }

        // ceiling and ground
        if (ball.posY > 1f) {
            ball.velocityY = -ball.velocityY;
        }
        if (ball.posY < -1f) {
            ball.velocityY = -ball.velocityY;
        }
    }

    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:
                playerOne.moveUp = true;
                break;
            case KeyEvent.VK_S:
                playerOne.moveDown = true;
                break;
            case KeyEvent.VK_P:
                playerTwo.moveUp = true;
                break;
            case KeyEvent.VK_L:
                playerTwo.moveDown = true;
                break;
            case KeyEvent.VK_SPACE:
                if (pauseGame) {
                    startGame();
                }
                break;
        }
    }

    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:
                playerOne.moveUp = false;
                break;
            case KeyEvent.VK_S:
                playerOne.moveDown = false;
                break;
            case KeyEvent.VK_P:
                playerTwo.moveUp = false;
                break;
            case KeyEvent.VK_L:
                playerTwo.moveDown = false;
                break;
        }
    }
}

abstract class GameObject {
    float[] vertices = Cube.geom;
    float angle;
    float rotation;
    float posX, posY;
    float sizeX, sizeY, sizeZ;

    public void display(GL2 gl) {
        gl.glLoadIdentity();
        gl.glTranslatef(posX, posY, -2f);
        gl.glScalef(this.sizeX, this.sizeY, this.sizeZ);

        // rotate the object
        angle += rotation;
        gl.glRotatef(angle, 0, 0, 1);

        gl.glBegin(GL2GL3.GL_QUADS);
        for (int i = 0; i < vertices.length; i += 3) {
            // check if side changed
            if (i % 12 == 0) {
                int side = i / 12;
                setColor(side, gl);
            }
            gl.glVertex3f(vertices[i], vertices[i + 1], vertices[i + 2]);
        }
        gl.glEnd();
    }

    public void update() {
    }

    public void setColor(int side, GL2 gl) {
        // Set color based on the side of the object
        switch (side) {
            case 0:
                gl.glColor3f(0.95f, 0.95f, 0.95f); // Lighter white
                break;
            case 1:
                gl.glColor3f(0.1f, 0.1f, 0.1f); // Slightly lighter black
                break;
            case 2:
            case 3:
                gl.glColor3f(0.35f, 0.35f, 0.35f); // Slightly lighter gray for middle sides
                break;
            case 4:
            case 5:
                gl.glColor3f(0.70f, 0.70f, 0.70f); // Light gray for the other sides
                break;
        }
    }
}

    class Player extends GameObject {
    boolean moveUp, moveDown = false;
    final float ACCELERATION_VALUE = 0.012f;
    float acceleration;
    float velocity;
    float borderLeft, borderRight, borderUp, borderDown;

    public Player(float posX, float posY) {
        this.sizeX = 0.05f;
        this.sizeY = 0.35f;
        this.sizeZ = 0.025f;
        this.posX = posX;
        this.posY = posY;
    }

    public void update() {
        // Reset acceleration to zero
        acceleration = 0.0f;
        if (moveUp) {
            acceleration += ACCELERATION_VALUE;  // Increase acceleration upwards
        }
        if (moveDown) {
            acceleration -= ACCELERATION_VALUE;  // Decrease acceleration downwards
        }

        // Apply acceleration to velocity and dampen it
        velocity += acceleration;
        velocity *= 0.75; // Apply damping factor
        this.posY += velocity; // Update position based on velocity

        // Restrict player movement within the vertical boundaries
        if (this.posY > 0.8f) {
            this.posY = 0.8f;
        } else if (this.posY < -0.8f) {
            this.posY = -0.8f;
        }

        // Recalculate collision borders after movement
        this.borderLeft = this.posX - this.sizeX;
        this.borderRight = this.posX + this.sizeX;
        this.borderUp = this.posY + this.sizeY;
        this.borderDown = this.posY - this.sizeY;
    }
}

    class Ball extends GameObject {
    float velocityX, velocityY;
    float borderLeft, borderRight, borderUp, borderDown;

    public Ball() {
        this.sizeX = 0.05f;
        this.sizeY = 0.05f;
        this.sizeZ = 0.05f;
    }

    public void update() {
        this.posX += velocityX;
        this.posY += velocityY;

        // update collision border
        this.borderLeft = this.posX - this.sizeX;
        this.borderRight = this.posX + this.sizeX;
        this.borderUp = this.posY + this.sizeY;
        this.borderDown = this.posY - this.sizeY;
    }

    public void reset() {
        this.velocityX = 0;
        this.velocityY = 0;
        this.posX = 0;
        this.posY = 0;
        this.angle = 0;
        this.rotation = 0;
    }
}

class Court extends GameObject {
    public float rotationAngle = 0.0f;
    int texID = 0;
    public float t = 0.0f;

    public void init(GLAutoDrawable d) {
        texID = loadTexture(d, "src/interstellar.png");
    }

    public void display(GL2 gl) {
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glTranslatef(0.0f, 0.0f, -2f);
        gl.glScalef(16f / 9f, 1.0f, 1.0f);

        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, texID);

        // apply rotation to the court
        gl.glRotatef(rotationAngle, 0.0f, 1.0f, 0.0f);

        drawTexturedCube(gl);

        gl.glDisable(GL2.GL_TEXTURE_2D);

        // increase the rotation angle
        rotationAngle += 0.005f;
        gl.glPopMatrix();
    }

    private void drawTexturedCube(GL2 gl) {
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, texID);
        gl.glColor3f(1.0f, 0.0f, 0.0f);

        gl.glBegin(GL2.GL_POLYGON); // Front face
        gl.glTexCoord2f(0.25f, 0.50f);
        gl.glVertex3f(-1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.25f, 0.25f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);
        gl.glTexCoord2f(0.50f, 0.25f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f);
        gl.glTexCoord2f(0.50f, 0.50f);
        gl.glVertex3f(1.0f, 1.0f, 1.0f);
        gl.glEnd();

        gl.glColor3f(1.0f, 1.0f, 0.0f);
        gl.glBegin(GL2.GL_POLYGON); // Back face
        gl.glTexCoord2f(0.00f, 0.50f);
        gl.glVertex3f(-1.0f, 1.0f, -1.0f);
        gl.glTexCoord2f(0.00f, 0.25f);
        gl.glVertex3f(-1.0f, -1.0f, -1.0f);
        gl.glTexCoord2f(0.25f, 0.25f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);
        gl.glTexCoord2f(0.25f, 0.50f);
        gl.glVertex3f(-1.0f, 1.0f, 1.0f);
        gl.glEnd();

        gl.glColor3f(0.0f, 0.0f, 1.0f);
        gl.glBegin(GL2.GL_POLYGON); // Right face
        gl.glTexCoord2f(0.50f, 0.50f);
        gl.glVertex3f(1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.50f, 0.25f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f);
        gl.glTexCoord2f(0.75f, 0.25f);
        gl.glVertex3f(1.0f, -1.0f, -1.0f);
        gl.glTexCoord2f(0.75f, 0.50f);
        gl.glVertex3f(1.0f, 1.0f, -1.0f);
        gl.glEnd();

        gl.glColor3f(1.0f, 1.0f, 1.0f);
        gl.glBegin(GL2.GL_POLYGON); // Top face
        gl.glTexCoord2f(0.25f, 0.75f);
        gl.glVertex3f(-1.0f, 1.0f, -1.0f);
        gl.glTexCoord2f(0.25f, 0.50f);
        gl.glVertex3f(-1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.50f, 0.50f);
        gl.glVertex3f(1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.50f, 0.75f);
        gl.glVertex3f(1.0f, 1.0f, -1.0f);
        gl.glEnd();

        gl.glColor3f(0.0f, 1.0f, 1.0f);
        gl.glBegin(GL2.GL_POLYGON); // Bottom face
        gl.glTexCoord2f(0.25f, 0.25f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);
        gl.glTexCoord2f(0.25f, 0.00f);
        gl.glVertex3f(-1.0f, -1.0f, -1.0f);
        gl.glTexCoord2f(0.50f, 0.00f);
        gl.glVertex3f(1.0f, -1.0f, -1.0f);
        gl.glTexCoord2f(0.50f, 0.25f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f);
        gl.glEnd();

        gl.glColor3f(0.0f, 1.0f, 0.0f);
        gl.glBegin(GL2.GL_POLYGON); // Left face
        gl.glTexCoord2f(0.75f, 0.50f);
        gl.glVertex3f(1.0f, 1.0f, -1.0f);
        gl.glTexCoord2f(0.75f, 0.25f);
        gl.glVertex3f(1.0f, -1.0f, -1.0f);
        gl.glTexCoord2f(1.00f, 0.25f);
        gl.glVertex3f(-1.0f, -1.0f, -1.0f);
        gl.glTexCoord2f(1.00f, 0.50f);
        gl.glVertex3f(-1.0f, 1.0f, -1.0f);
        gl.glEnd();

        gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    // Provides a valid texture ID upon successful loading, otherwise returns 0
    private int loadTexture(GLAutoDrawable drawable, String filePath) {
        GL2 gl = drawable.getGL().getGL2(); // Acquire the OpenGL 2 graphics context

        int width, height;
        int textureLevel = 0;
        int textureBorder = 0;

        try {
            // Access the file
            FileInputStream inputStream = new FileInputStream(new File(filePath));

            // Load the image data
            BufferedImage image = ImageIO.read(inputStream);
            inputStream.close();

            width = image.getWidth();
            height = image.getHeight();

            // Prepare the image data for OpenGL by converting to a ByteBuffer
            int[] pixelData = new int[width * height];
            image.getRGB(0, 0, width, height, pixelData, 0, width);
            ByteBuffer buffer = ByteBuffer.allocateDirect(pixelData.length * 4);
            buffer.order(ByteOrder.nativeOrder());

            // Encode each pixel's data into the ByteBuffer
            for (int y = 0; y < height; y++) {
                int pixelIndex = (height - 1 - y) * width;
                for (int x = 0; x < width; x++) {
                    int pixel = pixelData[pixelIndex++];
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green component
                    buffer.put((byte) (pixel & 0xFF));         // Blue component
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component
                }
            }
            buffer.flip(); // Prepare the buffer for reading

            // Set texture alignment configurations
            gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, 1);

            // Generate and bind a new texture ID
            final int[] textureIds = new int[1];
            gl.glGenTextures(1, textureIds, 0);
            gl.glBindTexture(GL2.GL_TEXTURE_2D, textureIds[0]);

            // Set texture filtering options
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);

            // Define the texture environment
            gl.glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);

            // Assign the texture data to the active texture object
            gl.glTexImage2D(GL2.GL_TEXTURE_2D, textureLevel, GL2.GL_RGB, width, height, textureBorder, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, buffer);

            return textureIds[0];
        } catch (FileNotFoundException ex) {
            System.out.println("The specified texture file was not found: " + filePath);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}

class Score extends GameObject {
    private int score = 0;

    float[] score0Data = {0.06f, 0.1f, 0.04f, 0.1f, 0.04f, -0.1f, 0.06f, -0.1f, -0.04f, 0.1f, -0.06f, 0.1f, -0.06f,
            -0.1f, -0.04f, -0.1f, 0.05f, 0.1f, 0.05f, 0.08f, -0.05f, 0.08f, -0.05f, 0.1f, 0.05f, -0.08f, 0.05f, -0.1f,
            -0.05f, -0.1f, -0.05f, -0.08f};

    float[] score1Data = {0.01f, 0.1f, -0.01f, 0.1f, -0.01f, -0.1f, 0.01f, -0.1f};

    float[] score2Data = {0.06f, 0.1f, 0.04f, 0.1f, 0.04f, 0.0f, 0.06f, 0.0f, -0.04f, 0.0f, -0.06f, 0.0f, -0.06f,
            -0.1f, -0.04f, -0.1f, 0.05f, 0.1f, 0.05f, 0.08f, -0.05f, 0.08f, -0.05f, 0.1f, 0.05f, -0.08f, 0.05f, -0.1f,
            -0.05f, -0.1f, -0.05f, -0.08f, 0.05f, 0.01f, 0.05f, -0.01f, -0.05f, -0.01f, -0.05f, 0.01f};

    float[] score3Data = {0.06f, 0.1f, 0.04f, 0.1f, 0.04f, -0.1f, 0.06f, -0.1f, 0.05f, 0.1f, 0.05f, 0.08f, -0.05f,
            0.08f, -0.05f, 0.1f, 0.05f, -0.08f, 0.05f, -0.1f, -0.05f, -0.1f, -0.05f, -0.08f, 0.05f, 0.01f, 0.05f,
            -0.01f, -0.05f, -0.01f, -0.05f, 0.01f};

    public Score(float posX, float posY) {
        this.setScore(this.score);
        this.posX = posX;
        this.posY = posY;
    }

    public void setScore(int score) {
        // Ignore scores exceeding 3
        if (score > 3) {
            return;
        }
        this.score = score;
        // Assign vertex data based on the score value
        switch (score) {
            case 0:
                this.vertices = score0Data;
                break;
            case 1:
                this.vertices = score1Data;
                break;
            case 2:
                this.vertices = score2Data;
                break;
            case 3:
                this.vertices = score3Data;
                break;
        }
    }


    public int getScore() {
        return this.score;
    }

    @Override
    public void display(GL2 gl) {
        gl.glLoadIdentity();
        gl.glTranslatef(posX, posY, -2f);
        gl.glBegin(GL2.GL_QUADS);
        for (int i = 0; i < vertices.length; i += 2) {
            gl.glVertex2f(vertices[i], vertices[i + 1]);
        }
        gl.glEnd();
    }
}

class Cube {
    static float[] geom = {
            // front
            1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f,

            // back
            1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f,

            // top
            1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f,

            // bottom
            1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f,

            // left
            -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f,

            // right
            1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f};

    static float[] textureCoords = {
            // front
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,

            // back
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,

            // top
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,

            // bottom
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,

            // left
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,

            // right
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f};
}
