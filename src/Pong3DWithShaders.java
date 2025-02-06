import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.Timer;
import javax.imageio.ImageIO;
import javax.swing.*;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.*;

import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.math.Matrix4f;
import com.jogamp.opengl.util.FPSAnimator;

public class Pong3DWithShaders {
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
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

    public void createGUI() {
        setTitle("PongShadow");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GLProfile glp = GLProfile.getDefault();

        // Some new Macs or laptops with ARM CPUs do not support OpenGL 3,
        // so we have to use OpenGL 4 instead:
        // GLProfile glp = GLProfile.get(GLProfile.GL4);

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
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context
        // enable depth test
        gl.glEnable(GL3.GL_DEPTH_TEST);

        // setup camera
        float aspect = 16.0f / 9.0f;
        // this function replaces gluPerspective
        game.projection.setToPerspective((float) Math.toRadians(60.0f), aspect, 1.5f, 5.5f);
        game.init(d);
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int width, int height) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context
        float windowAspect = (float) width / (float) height;
        float targetAspect = 16.0f / 9.0f;
        if (windowAspect >= targetAspect) {
            int correctedWidth = Math.round(height * targetAspect);
            int offsetX = Math.round((width - correctedWidth) / 2.0f);
            gl.glViewport(offsetX, 0, correctedWidth, height);
        } else {
            int correctedHeight = Math.round(width / targetAspect);
            int offsetY = Math.round((height - correctedHeight) / 2.0f);
            gl.glViewport(0, offsetY, width, correctedHeight);
        }
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
    VboLoader vboLoader = new VboLoader();
    Matrix4f projection = new Matrix4f();

    float[] lightDirection = new float[]{0, 0, -1};
    boolean followBall = false;
    float metallic = 0.0f;
    float roughness = 0.1f;

    // gameobjects
    Player playerOne;
    Score scoreOne;
    Player playerTwo;
    Score scoreTwo;
    Ball ball;
    PowerUp powerUp;
    Court court;


    Timer timer; // for power-up handling

    Shader shader;

    int shading = 0;

    ArrayList<GameObject> gameObjects = new ArrayList<>();

    public Game() {
        // Instantiate game elements
        ball = new Ball();
        playerOne = new Player(-1.8f, 0f, -90);
        scoreOne = new Score(-0.2f, 0.85f, 0.3f);
        playerTwo = new Player(1.8f, 0f, 90);
        scoreTwo = new Score(0.2f, 0.85f, 0.3f);
        court = new Court();
        powerUp = new PowerUp();

        // populate gameobject list
        gameObjects.add(court);
        gameObjects.add(ball);
        gameObjects.add(playerOne);
        gameObjects.add(playerTwo);
        gameObjects.add(scoreOne);
        gameObjects.add(scoreTwo);

        shader = new Shader();
    }

    public void init(GLAutoDrawable d) {
        // load VBOs
        vboLoader.loadVBO(d, "src/ball.vbo");
        ball.vertBufID = vboLoader.vertBufID;
        ball.vertNo = vboLoader.vertNo;

        vboLoader.loadVBO(d, "src/player.vbo");
        playerOne.vertBufID = vboLoader.vertBufID;
        playerOne.vertNo = vboLoader.vertNo;
        playerTwo.vertBufID = vboLoader.vertBufID;
        playerTwo.vertNo = vboLoader.vertNo;

        vboLoader.loadVBO(d, "src/court.vbo");
        court.vertBufID = vboLoader.vertBufID;
        court.vertNo = vboLoader.vertNo;

        int[] vertBufIDs = new int[4];
        int[] vertNos = new int[4];

        vboLoader.loadVBO(d, "src/0.vbo");
        vertBufIDs[0] = vboLoader.vertBufID;
        vertNos[0] = vboLoader.vertNo;
        vboLoader.loadVBO(d, "src/1.vbo");
        vertBufIDs[1] = vboLoader.vertBufID;
        vertNos[1] = vboLoader.vertNo;
        vboLoader.loadVBO(d, "src/2.vbo");
        vertBufIDs[2] = vboLoader.vertBufID;
        vertNos[2] = vboLoader.vertNo;
        vboLoader.loadVBO(d, "src/3.vbo");
        vertBufIDs[3] = vboLoader.vertBufID;
        vertNos[3] = vboLoader.vertNo;
        scoreOne.vertBufIDs = vertBufIDs;
        scoreOne.vertNos = vertNos;
        scoreOne.setScore(0);
        scoreTwo.vertBufIDs = vertBufIDs;
        scoreTwo.vertNos = vertNos;
        scoreTwo.setScore(0);

        vboLoader.loadVBO(d, "src/box_tri.vbo");
        powerUp.vertBufIDs[0] = vboLoader.vertBufID;
        powerUp.vertNos[0] = vboLoader.vertNo;

        // load shaders
        shader.setupShaders(d);

        // setup textures
        court.texID = TextureLoader.loadTexture(d, "src/interstellar.png");
        int texId = TextureLoader.loadTexture(d, "src/white.png");
        ball.texID = texId;
        playerOne.texID = texId;
        playerTwo.texID = texId;
        scoreOne.texID = texId;
        scoreTwo.texID = texId;
        powerUp.texIDs[0] = TextureLoader.loadTexture(d, "src/powerup_icons_grow.png");
        powerUp.texIDs[1] = TextureLoader.loadTexture(d, "src/powerup_icons_shrink.png");
        powerUp.texIDs[2] = TextureLoader.loadTexture(d, "src/powerup_icons_star.png");
        powerUp.setType(0);
    }

    public void display(GLAutoDrawable d) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context

        // clear the screen
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        gl.glUseProgram(shader.progID);

        // load the current shading mode into the corresponding UNIFORM
        gl.glUniform1i(shader.shadingLoc, shading);

        // load the current projection matrix into the corresponding UNIFORM
        gl.glUniformMatrix4fv(shader.projectionLoc, 1, false, projection.get(new float[16]), 0);
        gl.glUniform3f(shader.lightDirectionLoc, lightDirection[0], lightDirection[1], lightDirection[2]);
        gl.glUniform1f(shader.metallicLoc, metallic);
        gl.glUniform1f(shader.roughnessLoc, roughness);

        for (GameObject gameObject : gameObjects) {
            gameObject.shadowMode = false;
            gameObject.shading = shading;
            renderGameObject(gl, gameObject);
            if (!gameObject.equals(court)) {
                gameObject.shadowMode = true;
                renderGameObject(gl, gameObject);
            }
        }
    }

    private void renderGameObject(GL3 gl, GameObject gameObject) {
        // setup modelview transformation
        Matrix4f modelview = new Matrix4f();
        modelview.loadIdentity();

        if (gameObject.shadowMode) {
            gl.glUniform1i(shader.shadowLoc, 1);
            modelview.translate(gameObject.posX, gameObject.posY, -2.25f, new Matrix4f());
            modelview.scale(gameObject.sizeX, gameObject.sizeY, 0.0f, new Matrix4f());
        } else {
            gl.glUniform1i(shader.shadowLoc, 0);
            modelview.translate(gameObject.posX, gameObject.posY, -2.0f, new Matrix4f());
            modelview.scale(gameObject.sizeX, gameObject.sizeY, gameObject.sizeZ, new Matrix4f());
        }

        modelview.rotate((float) Math.toRadians(gameObject.angleX), 1, 0, 0, new Matrix4f());
        modelview.rotate((float) Math.toRadians(gameObject.angleY), 0, 1, 0, new Matrix4f());
        modelview.rotate((float) Math.toRadians(gameObject.angleZ), 0, 0, 1, new Matrix4f());
        gl.glUniformMatrix4fv(shader.modelviewLoc, 1, false, modelview.get(new float[16]), 0);

        // rotational part of the transformation
        modelview.transpose();
        modelview.invert();
        gl.glUniformMatrix4fv(shader.normalMatLoc, 1, false, modelview.get(new float[16]), 0);

        // setup texture
        gl.glEnable(GL3.GL_TEXTURE_2D);
        // activate texture unit 0
        gl.glActiveTexture(GL3.GL_TEXTURE0);
        // bind texture
        gl.glBindTexture(GL3.GL_TEXTURE_2D, gameObject.texID);
        // inform the shader to use texture unit 0
        gl.glUniform1i(shader.texLoc, 0);

        // activate VBO
        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, gameObject.vertBufID);
        int stride = (3 + 4 + 2 + 3) * Buffers.SIZEOF_FLOAT;
        int offset = 0;

        // position
        gl.glVertexAttribPointer(shader.vertexLoc, 3, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(shader.vertexLoc);

        // color
        offset = 3 * Buffers.SIZEOF_FLOAT;
        gl.glVertexAttribPointer(shader.colorLoc, 4, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(shader.colorLoc);

        // texture
        offset = (3 + 4) * Buffers.SIZEOF_FLOAT;
        gl.glVertexAttribPointer(shader.texCoordLoc, 2, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(shader.texCoordLoc);

        // normals
        offset = (3 + 4 + 2) * Buffers.SIZEOF_FLOAT;
        gl.glVertexAttribPointer(shader.normalLoc, 3, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(shader.normalLoc);

        // render data
        gl.glDrawArrays(GL3.GL_TRIANGLES, 0, gameObject.vertNo);

        gl.glDisable(GL3.GL_TEXTURE_2D);
    }

    public void update() {
        // update light direction
        if (followBall) {
            lightDirection = new float[]{ball.posX, ball.posX, -2};
        }

        for (GameObject gameObject : gameObjects) {
            gameObject.update();
            gameObject.angleY += gameObject.rotationY;
            gameObject.angleZ += gameObject.rotationZ;
        }
        checkCollisionBallPlayer();
        checkCollisionBallBorder();
        checkCollisionBallPowerUp();

        // spawn power up
        if (Util.rand.nextInt(10000) > 9975 && (ball.posY > 0.2f || ball.posY < -0.02f)) {
            spawnPowerUp();
        }
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
        removePowerUp();

        score.setScore(score.getScore() + 1);
        ball.reset();
        pauseGame = true;
    }

    public void spawnPowerUp() {
        if (!powerUp.spawned && !powerUp.taken) {
            powerUp.setRandomValues();
            gameObjects.add(powerUp);
            powerUp.spawned = true;
        }
    }

    public void removePowerUp() {
        for (int i = 0; i < gameObjects.size(); i++) {
            if (gameObjects.get(i) instanceof PowerUp) {
                gameObjects.remove(i);
                break;
            }
        }
        powerUp.spawned = false;
    }

    public void checkCollisionBallPlayer() {
        // collision player one
        if (ball.borderLeft < playerOne.borderRight && ball.borderLeft > playerOne.borderLeft) {
            if (ball.borderDown < playerOne.borderUp && ball.borderUp > playerOne.borderDown) {
                // calc hit positions distance to center
                float distanceToCenter = Math.abs(Math.abs(ball.posY) - Math.abs(playerOne.posY));
                if (ball.borderLeft < playerOne.borderRight - distanceToCenter * 0.125f) {
                    ball.posX = (playerOne.borderRight - distanceToCenter * 0.125f) + ball.scaleX;
                    // rotate ball
                    ball.rotationZ = playerOne.velocity * 273;
                    // reflect ball
                    ball.velocityX = -(ball.velocityX + (ball.rotationZ * .0005f));
                    ball.velocityY += (ball.rotationZ * .0015f);
                }
            }
        }

        // collision player two
        if (ball.borderRight > playerTwo.borderLeft && ball.borderRight < playerTwo.borderRight) {
            if (ball.borderDown < playerTwo.borderUp && ball.borderUp > playerTwo.borderDown) {
                float distanceToCenter = Math.abs(Math.abs(ball.posY) - Math.abs(playerTwo.posY));
                if (ball.borderRight > playerTwo.borderLeft + distanceToCenter * 0.125f) {
                    ball.posX = (playerTwo.borderLeft + distanceToCenter * 0.125f) - ball.scaleX;
                    // rotate ball
                    ball.rotationZ = playerTwo.velocity * 273;
                    // reflect ball
                    ball.velocityX = -ball.velocityX + (ball.rotationZ * .0005f);
                    ball.velocityY += (ball.rotationZ * .0015f);
                }
            }
        }
    }

    public void checkCollisionBallBorder() {
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

    public void checkCollisionBallPowerUp() {
        if (powerUp.spawned) {
            if (Math.abs(powerUp.posX - ball.posX) < powerUp.sizeX + ball.sizeX
                    && Math.abs(powerUp.posY - ball.posY) < powerUp.sizeY + ball.sizeY) {
                if (ball.velocityX < 0) {
                    powerUp.applyPowerUp(playerTwo, playerOne);
                } else {
                    powerUp.applyPowerUp(playerOne, playerTwo);
                }
                timer = new Timer(true);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        powerUp.removePowerUp();
                        powerUp.taken = false;
                        timer.cancel();
                    }
                }, 4000);

                removePowerUp();
                powerUp.taken = true;
            }
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
            case KeyEvent.VK_0:
                lightDirection = new float[]{0, 0, -1};
                followBall = false;
                break;
            case KeyEvent.VK_1:
                lightDirection = new float[]{0, -1, 0};
                followBall = false;
                break;
            case KeyEvent.VK_2:
                lightDirection = new float[]{0, 1, 0};
                followBall = false;
                break;
            case KeyEvent.VK_3:
                lightDirection = new float[]{-1, -1, 0};
                followBall = false;
                break;
            case KeyEvent.VK_4:
                followBall = true;
                break;
            case KeyEvent.VK_5:
                metallic = 0.0f;
                break;
            case KeyEvent.VK_6:
                metallic = 1.0f;
                break;
            case KeyEvent.VK_7:
                roughness = 0.1f;
                break;
            case KeyEvent.VK_8:
                roughness = 0.2f;
                break;
            case KeyEvent.VK_9:
                shading++;
                if (shading > 2) {
                    shading = 0;
                }

                switch (shading) {
                    case 0:
                        System.out.println("Using: PBR (Default) Shader");
                        break;
                    case 1:
                        System.out.println("Using: Toon Shader");
                        break;
                    case 2:
                        System.out.println("Using: Noise Shader");
                        break;
                }
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
    int vertBufID;
    int vertNo;
    int texID;

    float angleX, angleY, angleZ;
    float rotationY, rotationZ;
    float posX, posY;
    float sizeX, sizeY, sizeZ;

    boolean shadowMode;
    int shading = 1;

    public void update() {
    }
}

class Player extends GameObject {
    boolean moveUp, moveDown = false;
    float ACCELERATION_VALUE = 0.012f;
    float acceleration;
    float velocity;
    float borderLeft, borderRight, borderUp, borderDown;
    float scaleX, scaleY, scaleZ;

    public Player(float posX, float posY, float angleZ) {
        this.scaleX = 0.35f;
        this.scaleY = 0.35f;
        this.scaleZ = 0.35f;
        this.sizeX = this.scaleX * 2;
        this.sizeY = this.scaleY * 2;
        this.sizeZ = this.scaleZ * 2;
        this.posX = posX;
        this.posY = posY;
        this.angleZ = angleZ;
    }

    public void setScaleY(float scaleY) {
        this.scaleY = scaleY;
        this.sizeY = this.scaleY * 2;
    }

    public void update() {
        acceleration = 0.0f;
        if (moveUp) {
            acceleration += ACCELERATION_VALUE;
        }
        if (moveDown) {
            acceleration += -ACCELERATION_VALUE;
        }

        velocity += acceleration;
        velocity *= 0.75f;
        this.posY += velocity;

        if (this.posY >= 0.8f) {
            this.posY = 0.8f;
        }
        if (this.posY <= -0.8f) {
            this.posY = -0.8f;
        }

        // update collision border
        this.borderLeft = this.posX - this.scaleX / 4f;
        this.borderRight = this.posX + this.scaleX / 4f;

        this.borderUp = this.posY + this.scaleY;
        this.borderDown = this.posY - this.scaleY;
    }
}

class Ball extends GameObject {
    float velocityX, velocityY;
    float borderLeft, borderRight, borderUp, borderDown;
    float scaleX, scaleY, scaleZ;

    public Ball() {
        this.scaleX = this.scaleY = this.scaleZ = 0.075f;
        this.sizeX = this.sizeY = this.sizeZ = this.scaleX * 2;
    }

    public void update() {
        this.posX += velocityX;
        this.posY += velocityY;

        // update collision border
        this.borderLeft = this.posX - this.scaleX;
        this.borderRight = this.posX + this.scaleX;
        this.borderUp = this.posY + this.scaleY;
        this.borderDown = this.posY - this.scaleY;
    }

    public void reset() {
        this.velocityX = 0;
        this.velocityY = 0;
        this.posX = 0;
        this.posY = 0;
        this.angleZ = 0;
        this.rotationZ = 0;
    }
}

class PowerUp extends GameObject {
    float velocity;
    int type;
    int[] texIDs = new int[3];
    int[] vertBufIDs = new int[1];
    int[] vertNos = new int[1];
    Player lastConsumerPlayer;
    Player lastOtherPlayer;
    boolean spawned;
    boolean taken;

    public PowerUp() {
        this.sizeX = this.sizeY = this.sizeZ = 0.1f;
        spawned = false;
        taken = false;
    }

    public void setType(int powerUpType) {
        type = powerUpType;
        this.texID = texIDs[powerUpType];
        this.vertBufID = vertBufIDs[0];
        this.vertNo = vertNos[0];
    }

    public void setRandomValues() {
        // set random velocity
        velocity = Util.rand.nextInt(1000) / 1000f * 0.01f;
        // set random type
        var randomInt = Util.rand.nextInt(2);
        setType(randomInt);
    }

    public void update() {
        if (posY > 1f) {
            posY = 1f;
            velocity = -velocity;
        }
        if (posY < -1f) {
            posY = -1f;
            velocity = -velocity;
        }
        posY += velocity;
    }

    public void applyPowerUp(Player consumer, Player other) {
        switch (type) {
            case 0:
                consumer.setScaleY(consumer.scaleY * 2);
                break;
            case 1:
                other.setScaleY(other.scaleY / 2);
                break;
            case 2:
                consumer.ACCELERATION_VALUE *= 2;
                break;
        }
        lastConsumerPlayer = consumer;
        lastOtherPlayer = other;
    }

    public void removePowerUp() {
        switch (type) {
            case 0:
                lastConsumerPlayer.setScaleY(lastConsumerPlayer.scaleY / 2);
                break;
            case 1:
                lastOtherPlayer.setScaleY(lastOtherPlayer.scaleY * 2);
                break;
            case 2:
                lastConsumerPlayer.ACCELERATION_VALUE /= 2;
                break;
        }
    }
}

class Court extends GameObject {
    public Court() {
        this.rotationY = -0.01f;
        this.sizeX = this.sizeY = this.sizeZ = 2f;
        this.shading = 0;
    }

    public void update() {
        this.angleY += rotationY;
    }
}

class Score extends GameObject {
    private int score = 0;
    int[] vertBufIDs;
    int[] vertNos;

    public Score(float posX, float posY, float size) {
        this.posX = posX;
        this.posY = posY;
        this.sizeX = size;
        this.sizeY = size;
        this.sizeZ = size;
    }

    public void setScore(int score) {
        if (score > 3) {
            return;
        }
        this.score = score;
        if (score < vertBufIDs.length) {
            vertBufID = vertBufIDs[score];
            vertNo = vertNos[score];
        }
    }

    public int getScore() {
        return this.score;
    }
}

class Util {
    static Random rand = new Random();
}

class TextureLoader {
    // returns a valid textureID on success, otherwise -1
    static int loadTexture(GLAutoDrawable d, String filename) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context

        int width;
        int height;
        int level = 0;
        int border = 0;

        try {
            // open file
            FileInputStream fileInputStream = new FileInputStream(filename);
            // read image
            BufferedImage bufferedImage = ImageIO.read(fileInputStream);
            fileInputStream.close();

            width = bufferedImage.getWidth();
            height = bufferedImage.getHeight();
            int[] pixelIntData = new int[width * height];
            // convert image to ByteBuffer
            bufferedImage.getRGB(0, 0, width, height, pixelIntData, 0, width);
            ByteBuffer buffer = ByteBuffer.allocateDirect(pixelIntData.length * 4);
            buffer.order(ByteOrder.nativeOrder());

            // Unpack the data, each integer into 4 bytes of the ByteBuffer.
            // Also we need to vertically flip the image because the image origin
            // in OpenGL is the lower-left corner.
            for (int y = 0; y < height; y++) {
                int k = (height - 1 - y) * width;
                for (int x = 0; x < width; x++) {
                    buffer.put((byte) (pixelIntData[k] >>> 16));
                    buffer.put((byte) (pixelIntData[k] >>> 8));
                    buffer.put((byte) (pixelIntData[k]));
                    buffer.put((byte) (pixelIntData[k] >>> 24));
                    k++;
                }
            }
            buffer.rewind();

            // data is aligned in byte order
            gl.glPixelStorei(GL3.GL_UNPACK_ALIGNMENT, 1);

            // request textureID
            final int[] textureID = new int[1];
            gl.glGenTextures(1, textureID, 0);

            // bind texture
            gl.glBindTexture(GL3.GL_TEXTURE_2D, textureID[0]);

            // define how to filter the texture
            gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_LINEAR);
            gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_LINEAR);


            // specify the 2D texture map
            gl.glTexImage2D(GL3.GL_TEXTURE_2D, level, GL3.GL_RGB, width, height, border, GL3.GL_RGBA,
                    GL3.GL_UNSIGNED_BYTE, buffer);

            return textureID[0];
        } catch (FileNotFoundException e) {
            System.out.println("Can not find texture data file " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return -1;
    }
}

class VboLoader {
    int vertBufID;
    int vertNo;

    public void loadVBO(GLAutoDrawable d, String filename) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context
        int perVertexFloats = (3 + 4 + 2 + 3);
        float[] vertexData = loadVertexData(filename, perVertexFloats);

        // generate VBO
        int[] vboID = new int[1];
        gl.glGenBuffers(1, vboID, 0);
        vertBufID = vboID[0];
        vertNo = vertexData.length / perVertexFloats;
        FloatBuffer dataIn = Buffers.newDirectFloatBuffer(vertexData.length);
        dataIn.put(vertexData);
        dataIn.flip();

        // bind buffers
        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufID);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn.capacity() * Buffers.SIZEOF_FLOAT, dataIn, GL3.GL_STATIC_DRAW);
    }

    private float[] loadVertexData(String filename, int perVertexFloats) {
        float[] floatArray = new float[0];

        // read vertex data from file
        int vertSize = 0;
        try {
            InputStream is = new FileInputStream(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line = br.readLine();
            if (line != null) {
                vertSize = Integer.parseInt(line);
                floatArray = new float[vertSize];
            }
            int i = 0;
            while ((line = br.readLine()) != null && i < floatArray.length) {
                floatArray[i] = Float.parseFloat(line);
                i++;
            }
            if (i != vertSize || (vertSize % perVertexFloats) != 0) {
                floatArray = new float[0];
            }
            br.close();
        } catch (FileNotFoundException e) {
            System.out.println("Can not find vbo data file " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return floatArray;
    }
}

class Shader {
    int progID = 0;

    int vertexLoc = -1;
    int colorLoc = -1;
    int texCoordLoc = -1;
    int normalLoc = -1;
    int projectionLoc = -1;
    int modelviewLoc = -1;
    int normalMatLoc = -1;
    int texLoc = -1;
    int lightDirectionLoc = 0;
    int metallicLoc = -1;
    int roughnessLoc = -1;
    int shadowLoc = -1;
    int shadingLoc = -0;

    public void setupShaders(GLAutoDrawable d) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 3 graphics context

        int textVertID = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
        int textFragID = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);

        String[] vs = new String[]{
                """
#version 140

in vec3 inputPosition;
in vec4 inputColor;
in vec2 inputTexCoord;
in vec3 inputNormal;

uniform mat4 projection;
uniform mat4 modelview;
uniform mat4 normalMat;

out vec3 forFragColor;
out vec2 forFragTexCoord;
out vec3 normal;
out vec3 vertPos;

void main(){
    forFragColor = inputColor.rgb;
    forFragTexCoord = inputTexCoord;
    normal = (normalMat * vec4(inputNormal, 0.0)).xyz;
    vec4 vertPos4 = modelview * vec4(inputPosition, 1.0);
    vertPos = vec3(vertPos4) / vertPos4.w;
    gl_Position =  projection * modelview * vec4(inputPosition, 1.0);
}
"""
        };

        String[] fs = new String[]{
                """
#version 140
out vec4 outputColor;

in vec2 forFragTexCoord;
in vec3 normal;
in vec3 vertPos;
in vec3 forFragColor;

uniform sampler2D myTexture;
uniform vec3 lightDirection;
uniform float metallic;
uniform float roughness;
uniform bool shadow;
uniform int shading;

const vec4 lightColor = vec4(1.0, 1.0, 1.0, 1.0);
const vec3 ambientLight = vec3(0.1, 0.1, 0.1);
const float reflectance = 0.5f;
const float irradiPerp = 5.0f;

#define RECIPROCAL_PI 0.3183098861837907

// Simple noise function based on UV coordinates
float noise(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

float D_GGX(float NoH, float roughness) {
    float alpha = roughness * roughness;
    float alpha2 = alpha * alpha;
    float NoH2 = NoH * NoH;
    float b = (NoH2 * (alpha2 - 1.0) + 1.0);
    return alpha2 * RECIPROCAL_PI / (b * b);
}

float G1_GGX_Schlick(float NoV, float roughness) {
    float alpha = roughness * roughness;
    float k = alpha / 2.0;
    return max(NoV, 0.001) / (NoV * (1.0 - k) + k);
}

float G_Smith(float NoV, float NoL, float roughness) {
    return G1_GGX_Schlick(NoL, roughness) * G1_GGX_Schlick(NoV, roughness);
}

vec3 microfacetBRDF(vec3 L, vec3 V, vec3 N, float metallic, float roughness, vec3 baseColor, float reflectance) {
    vec3 H = normalize(V + L);
    float NoV = clamp(dot(N, V), 0.0, 1.0);
    float NoL = clamp(dot(N, L), 0.0, 1.0);
    float NoH = clamp(dot(N, H), 0.0, 1.0);
    float VoH = clamp(dot(V, H), 0.0, 1.0);

    vec3 f0 = vec3(0.16 * (reflectance * reflectance));
    f0 = mix(f0, baseColor, metallic);

    vec3 F = fresnelSchlick(VoH, f0);
    float D = D_GGX(NoH, roughness);
    float G = G_Smith(NoV, NoL, roughness);

    vec3 spec = (F * D * G) / (4.0 * max(NoV, 0.001) * max(NoL, 0.001));
    vec3 rhoD = baseColor * (1.0 - F) * (1.0 - metallic);
    vec3 diff = rhoD * RECIPROCAL_PI;

    return diff + spec;
}

vec3 toonBRDF(vec3 lightDir, vec3 viewDir, vec3 normal, vec3 phongDiffuseCol, vec3 phongSpecularCol, float phongShininess) {
    vec3 color = phongDiffuseCol * 0.5; // Reduce diffuse brightness slightly

    vec3 halfDir = normalize(viewDir + lightDir);
    float specDot = max(dot(halfDir, normal), 0.0);

    float specIntensity = smoothstep(0.9, 0.99, pow(specDot, phongShininess));\s

    color += specIntensity * phongSpecularCol * 0.2;\s

    return color;
}

vec3 noiseShader(vec3 lightDir, vec3 normal, vec3 baseColor) {
    float n = noise(forFragTexCoord * 10.0);
    float intensity = max(dot(normal, lightDir), 0.0);
    return baseColor * (0.5 + 0.9 * n) * intensity;
}

void main() {
    if (shadow) {
        outputColor = vec4(0.1, 0.1, 0.1, 1.0);
    } else {
        vec3 n = normalize(normal);
        vec3 lightDir = normalize(-lightDirection);
        vec3 viewDir = normalize(-vertPos);

        vec3 textureColor = texture(myTexture, forFragTexCoord).rgb;
        vec3 baseColor = forFragColor * textureColor;
        baseColor = pow(baseColor, vec3(2.2)); // gamma correction

        vec3 radiance = ambientLight * baseColor;
        float irradiance = max(dot(lightDir, n), 0.0) * irradiPerp;

        if (irradiance > 0.0) {
            vec3 brdf;

            if (shading == 0) { // Physically Based Rendering (PBR)
                brdf = microfacetBRDF(lightDir, viewDir, n, metallic, roughness, baseColor, reflectance);
            }
            else if (shading == 1) { // Toon shading
                brdf = toonBRDF(lightDir, viewDir, n, baseColor, vec3(1.0), 8.0);
            }
            else if (shading == 2) { // Noise shader
                brdf = noiseShader(lightDir, n, baseColor);
            }
            else { 
                brdf = microfacetBRDF(lightDir, viewDir, n, metallic, roughness, baseColor, reflectance);
            }

            radiance += brdf * irradiance * lightColor.rgb;
        }

        radiance = pow(radiance, vec3(1.0 / 2.2)); // gamma correction

        outputColor = vec4(radiance, 1.0);
    }
}
"""
        };

        gl.glShaderSource(textVertID, 1, vs, null, 0);
        gl.glShaderSource(textFragID, 1, fs, null, 0);

        // compile the shader
        gl.glCompileShader(textVertID);
        gl.glCompileShader(textFragID);

        // check for errors
        printShaderInfoLog(d, textVertID);
        printShaderInfoLog(d, textFragID);

        // create program and attach shaders
        progID = gl.glCreateProgram();
        gl.glAttachShader(progID, textVertID);
        gl.glAttachShader(progID, textFragID);

        // "outColor" is a user-provided OUT variable
        // of the fragment shader.
        // Its output is bound to the first color buffer
        // in the framebuffer
        gl.glBindFragDataLocation(progID, 0, "outputColor");

        // link the program
        gl.glLinkProgram(progID);
        // output error messages
        printProgramInfoLog(d, progID);

        // "inputPosition" and "inputColor" are user-provided
        // IN variables of the vertex shader.
        // Their locations are stored to be used later with
        // glEnableVertexAttribArray()
        vertexLoc = gl.glGetAttribLocation(progID, "inputPosition");
        colorLoc = gl.glGetAttribLocation(progID, "inputColor");
        texCoordLoc = gl.glGetAttribLocation(progID, "inputTexCoord");
        normalLoc = gl.glGetAttribLocation(progID, "inputNormal");

        // "projection" and "modelview" are user-provided
        // UNIFORM variables of the vertex shader.
        // Their locations are stored to be used later
        projectionLoc = gl.glGetUniformLocation(progID, "projection");
        modelviewLoc = gl.glGetUniformLocation(progID, "modelview");
        normalMatLoc = gl.glGetUniformLocation(progID, "normalMat");
        texLoc = gl.glGetUniformLocation(progID, "myTexture");
        lightDirectionLoc = gl.glGetUniformLocation(progID, "lightDirection");
        metallicLoc = gl.glGetUniformLocation(progID, "metallic");
        roughnessLoc = gl.glGetUniformLocation(progID, "roughness");
        shadowLoc = gl.glGetUniformLocation(progID, "shadow");
        shadingLoc = gl.glGetUniformLocation(progID, "shading");
    }

    private static void printShaderInfoLog(GLAutoDrawable d, int obj) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 3 graphics context
        IntBuffer infoLogLengthBuf = IntBuffer.allocate(1);
        int infoLogLength;
        gl.glGetShaderiv(obj, GL3.GL_INFO_LOG_LENGTH, infoLogLengthBuf);
        infoLogLength = infoLogLengthBuf.get(0);
        if (infoLogLength > 0) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(infoLogLength);
            gl.glGetShaderInfoLog(obj, infoLogLength, infoLogLengthBuf, byteBuffer);
            for (byte b : byteBuffer.array()) {
                System.err.print((char) b);
            }
        }
    }

    private static void printProgramInfoLog(GLAutoDrawable d, int obj) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 3 graphics context
        IntBuffer infoLogLengthBuf = IntBuffer.allocate(1);
        int infoLogLength;
        gl.glGetProgramiv(obj, GL3.GL_INFO_LOG_LENGTH, infoLogLengthBuf);
        infoLogLength = infoLogLengthBuf.get(0);
        if (infoLogLength > 0) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(infoLogLength);
            gl.glGetProgramInfoLog(obj, infoLogLength, infoLogLengthBuf, byteBuffer);
            for (byte b : byteBuffer.array()) {
                System.err.print((char) b);
            }
        }
    }
}