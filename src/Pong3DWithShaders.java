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
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GL;

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
        setTitle("PongShaders");
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
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context
        // enable depth test
        gl.glEnable(gl.GL_DEPTH_TEST);

        // setup camera
        float aspect = 16.0f / 9.0f;
        // this function replaces gluPerspective
        game.projection.setToPerspective((float) Math.toRadians(60.0f), aspect, 1.5f, 5.5f);

        game.init(d);
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int width, int height) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context
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
    VBOLoader vboLoader = new VBOLoader();
    Matrix4f projection = new Matrix4f();
    ShaderLoader shaderLoader = new ShaderLoader();

    // gameobjects
    Player playerOne;
    Score scoreOne;
    Player playerTwo;
    Score scoreTwo;
    Ball ball;
    PowerUp powerUp;
    Court court;

    ArrayList<GameObject> gameObjects = new ArrayList<>();

    public Game() {
        // Instantiate game elements
        ball = new Ball();
        playerOne = new Player(-1.8f, 0f, -90);
        scoreOne = new Score(-0.2f, 0.85f, 0.3f);
        playerTwo = new Player(1.8f, 0f, 90);
        scoreTwo = new Score(0.2f, 0.85f, 0.3f);
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
        // load vbos
        vboLoader.initVBO(d);
        ball.vertBufID = vboLoader.vertBufIDs[0];
        ball.vertNo = vboLoader.vertNos[0];
        playerOne.vertBufID = vboLoader.vertBufIDs[1];
        playerOne.vertNo = vboLoader.vertNos[1];
        playerTwo.vertBufID = vboLoader.vertBufIDs[1];
        playerTwo.vertNo = vboLoader.vertNos[1];
        court.vertBufID = vboLoader.vertBufIDs[2];
        court.vertNo = vboLoader.vertNos[2];
        int[] vertBufIDs = new int[4];
        vertBufIDs[0] = vboLoader.vertBufIDs[3];
        vertBufIDs[1] = vboLoader.vertBufIDs[4];
        vertBufIDs[2] = vboLoader.vertBufIDs[5];
        vertBufIDs[3] = vboLoader.vertBufIDs[6];
        Score.vertBufIDs = vertBufIDs;
        int[] vertNos = new int[4];
        vertNos[0] = vboLoader.vertNos[3];
        vertNos[1] = vboLoader.vertNos[4];
        vertNos[2] = vboLoader.vertNos[5];
        vertNos[3] = vboLoader.vertNos[6];
        Score.vertNos = vertNos;

        // load shaders
        shaderLoader.setupShaders(d);

        // setup textures
        court.texID = Util.loadTexture(d, "src/interstellar.png");
        int texId = Util.loadTexture(d, "src/white.png");
        ball.texID = texId;
        playerOne.texID = texId;
        playerTwo.texID = texId;
        scoreOne.texID = texId;
        scoreTwo.texID = texId;
        PowerUp.texIDs[0] = Util.loadTexture(d, "src/powerup_icons_grow.png");
        PowerUp.texIDs[1] = Util.loadTexture(d, "src/powerup_icons_shrink.png");
        PowerUp.texIDs[2] = Util.loadTexture(d, "src/powerup_icons_star.png");
    }

    public void display(GLAutoDrawable d) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context

        // clear the screen
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        gl.glUseProgram(ShaderLoader.progID);
        // load the current projection matrix into the corresponding UNIFORM
        gl.glUniformMatrix4fv(ShaderLoader.projectionLoc, 1, false, projection.get(new float[16]), 0);
        for (GameObject gameObject : gameObjects) {
            gameObject.display(gl);
        }
        gl.glFlush();
    }

    public void update() {
        for (GameObject gameObject : gameObjects) {
            gameObject.update();
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
        if (!PowerUp.spawned && !PowerUp.taken) {
            powerUp = new PowerUp();
            powerUp.vertNo = vboLoader.vertNos[7];
            powerUp.vertBufID = vboLoader.vertBufIDs[7];
            gameObjects.add(powerUp);
            PowerUp.spawned = true;
        }
    }

    public void removePowerUp() {
        for (int i = 0; i < gameObjects.size(); i++) {
            if (gameObjects.get(i) instanceof PowerUp) {
                gameObjects.remove(i);
                break;
            }
        }
        PowerUp.spawned = false;
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
        if (PowerUp.spawned) {
            if (Math.abs(powerUp.posX - ball.posX) < powerUp.sizeX + ball.sizeX
                    && Math.abs(powerUp.posY - ball.posY) < powerUp.sizeY + ball.sizeY) {
                if (ball.velocityX < 0) {
                    powerUp.applyPowerUp(playerTwo, playerOne);
                } else {
                    powerUp.applyPowerUp(playerOne, playerTwo);
                }
                removePowerUp();
                PowerUp.taken = true;
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
    boolean isBox = false;

    Matrix4f modelview = new Matrix4f();
    float angleX, angleY, angleZ;
    float rotationY, rotationZ;
    float posX, posY;
    float sizeX, sizeY, sizeZ;

    public void display(GL3 gl) {

        // setup modelview transformation
        modelview.loadIdentity();
        modelview.translate(posX, posY, -2.0f, new Matrix4f());
        modelview.scale(sizeX, sizeY, sizeZ, new Matrix4f());
        modelview.rotate((float) Math.toRadians(angleX), 1, 0, 0, new Matrix4f());
        modelview.rotate((float) Math.toRadians(angleY), 0, 1, 0, new Matrix4f());
        modelview.rotate((float) Math.toRadians(angleZ), 0, 0, 1, new Matrix4f());

        gl.glUniformMatrix4fv(ShaderLoader.modelviewLoc, 1, false, modelview.get(new float[16]), 0);

        // setup texture
        gl.glEnable(GL3.GL_TEXTURE_2D);
        // activate texture unit 0
        gl.glActiveTexture(GL3.GL_TEXTURE0);
        // bind texture
        gl.glBindTexture(GL3.GL_TEXTURE_2D, texID);
        // inform the shader to use texture unit 0
        gl.glUniform1i(ShaderLoader.texLoc, 0);


        // activate VBO
        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufID);
        int stride = (3 + 4 + 2 + 3) * Buffers.SIZEOF_FLOAT;
        int offset = 0;

        // position
        gl.glVertexAttribPointer(ShaderLoader.vertexLoc, 3, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(ShaderLoader.vertexLoc);

        // color
        offset = 3 * Buffers.SIZEOF_FLOAT;
        gl.glVertexAttribPointer(ShaderLoader.colorLoc, 4, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(ShaderLoader.colorLoc);

        // texture
        offset = (3 + 4) * Buffers.SIZEOF_FLOAT;
        gl.glVertexAttribPointer(ShaderLoader.texCoordLoc, 2, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(ShaderLoader.texCoordLoc);

        // normals
        offset = (3 + 4 + 2) * Buffers.SIZEOF_FLOAT;
        gl.glVertexAttribPointer(ShaderLoader.normalLoc, 3, GL3.GL_FLOAT, false, stride, offset);
        gl.glEnableVertexAttribArray(ShaderLoader.normalLoc);

        if (isBox) {
            gl.glDrawArrays(GL3.GL_QUADS, 0, vertNo);
        } else {
            // render data
            gl.glDrawArrays(GL3.GL_TRIANGLES, 0, vertNo);
        }

        gl.glDisable(GL3.GL_TEXTURE_2D);
    }

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
        this.scaleZ = 0.05f;
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
        velocity *= 0.75;
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

        // Update rotation angle
        angleZ += rotationZ;

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
    Timer timer;
    static boolean taken = false;
    static boolean spawned = false;
    float velocity;
    int type;
    static int[] texIDs = new int[3];

    public PowerUp() {
        this.sizeX = this.sizeY = this.sizeZ = 0.1f;

        // set random velocity
        velocity = Util.rand.nextInt(1000) / 1000f * 0.05f;
        // set random type
        type = Util.rand.nextInt(2);
        this.texID = texIDs[type];
        this.isBox = true;
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
        timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                removePowerUp(consumer, other);
                PowerUp.taken = false;
                timer.cancel();
            }
        }, 4000);
    }

    public void removePowerUp(Player consumer, Player other) {
        switch (type) {
            case 0:
                consumer.setScaleY(consumer.scaleY / 2);
                break;
            case 1:
                other.setScaleY(other.scaleY * 2);
                break;
            case 2:
                consumer.ACCELERATION_VALUE /= 2;
                break;
        }
    }
}

class Court extends GameObject {
    public Court() {
        this.rotationY = -0.005f;
        this.sizeX = this.sizeY = this.sizeZ = 2f;
        this.isBox = true;
    }

    public void update() {
        this.angleY += rotationY;
    }
}

class Score extends GameObject {
    private int score = 0;
    static int[] vertBufIDs;
    static int[] vertNos;

    public Score(float posX, float posY, float size) {
        this.setScore(this.score);
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
    }

    public int getScore() {
        return this.score;
    }

    @Override
    public void display(GL3 gl) {
        // shouldn't be here
        vertBufID = vertBufIDs[score];
        vertNo = vertNos[score];
        super.display(gl);
    }
}

class Util {
    static Random rand = new Random();

    // returns a valid textureID on success, otherwise 0
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
            gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
            gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);


            // specify the 2D texture map
            gl.glTexImage2D(GL3.GL_TEXTURE_2D, level, GL3.GL_RGB, width, height, border, GL3.GL_RGBA,
                    GL3.GL_UNSIGNED_BYTE, buffer);

            return textureID[0];
        } catch (FileNotFoundException e) {
            System.out.println("Can not find texture data file " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }
}

class VBOLoader {
    int[] vertBufIDs;
    int[] vertNos;

    public void initVBO(GLAutoDrawable d) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 2 graphics context

        int perVertexFloats = (3 + 4 + 2 + 3);
        float[] vertexDataBall = loadVertexData("src/ball.vbo", perVertexFloats);
        float[] vertexDataBar = loadVertexData("src/bar.vbo", perVertexFloats);
        float[] vertexDataPowerUp = loadVertexData("src/box.vbo", perVertexFloats);
        float[] vertexDataCourt = loadVertexData("src/skybox.vbo", perVertexFloats);
        float[] vertexDataScore0 = loadVertexData("src/0.vbo", perVertexFloats);
        float[] vertexDataScore1 = loadVertexData("src/1.vbo", perVertexFloats);
        float[] vertexDataScore2 = loadVertexData("src/2.vbo", perVertexFloats);
        float[] vertexDataScore3 = loadVertexData("src/3.vbo", perVertexFloats);

        FloatBuffer[] dataIn = new FloatBuffer[8];
        vertBufIDs = new int[8];
        vertNos = new int[8];

        vertNos[0] = vertexDataBall.length / perVertexFloats;
        dataIn[0] = Buffers.newDirectFloatBuffer(vertexDataBall.length);
        dataIn[0].put(vertexDataBall);
        dataIn[0].flip();

        vertNos[1] = vertexDataBar.length / perVertexFloats;
        dataIn[1] = Buffers.newDirectFloatBuffer(vertexDataBar.length);
        dataIn[1].put(vertexDataBar);
        dataIn[1].flip();

        vertNos[2] = vertexDataCourt.length / perVertexFloats;
        dataIn[2] = Buffers.newDirectFloatBuffer(vertexDataCourt.length);
        dataIn[2].put(vertexDataCourt);
        dataIn[2].flip();

        vertNos[3] = vertexDataScore0.length / perVertexFloats;
        dataIn[3] = Buffers.newDirectFloatBuffer(vertexDataScore0.length);
        dataIn[3].put(vertexDataScore0);
        dataIn[3].flip();

        vertNos[4] = vertexDataScore1.length / perVertexFloats;
        dataIn[4] = Buffers.newDirectFloatBuffer(vertexDataScore1.length);
        dataIn[4].put(vertexDataScore1);
        dataIn[4].flip();

        vertNos[5] = vertexDataScore2.length / perVertexFloats;
        dataIn[5] = Buffers.newDirectFloatBuffer(vertexDataScore2.length);
        dataIn[5].put(vertexDataScore2);
        dataIn[5].flip();

        vertNos[6] = vertexDataScore3.length / perVertexFloats;
        dataIn[6] = Buffers.newDirectFloatBuffer(vertexDataScore3.length);
        dataIn[6].put(vertexDataScore3);
        dataIn[6].flip();

        vertNos[7] = vertexDataPowerUp.length / perVertexFloats;
        dataIn[7] = Buffers.newDirectFloatBuffer(vertexDataPowerUp.length);
        dataIn[7].put(vertexDataPowerUp);
        dataIn[7].flip();

        // generating vertex VBO
        gl.glGenBuffers(8, vertBufIDs, 0);

        // bind buffers
        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[0]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[0].capacity() * Buffers.SIZEOF_FLOAT, dataIn[0],
                GL3.GL_STATIC_DRAW);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[1]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[1].capacity() * Buffers.SIZEOF_FLOAT, dataIn[1],
                GL3.GL_STATIC_DRAW);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[2]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[2].capacity() * Buffers.SIZEOF_FLOAT, dataIn[2],
                GL3.GL_STATIC_DRAW);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[3]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[3].capacity() * Buffers.SIZEOF_FLOAT, dataIn[3],
                GL3.GL_STATIC_DRAW);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[4]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[4].capacity() * Buffers.SIZEOF_FLOAT, dataIn[4],
                GL3.GL_STATIC_DRAW);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[5]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[5].capacity() * Buffers.SIZEOF_FLOAT, dataIn[5],
                GL3.GL_STATIC_DRAW);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[6]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[6].capacity() * Buffers.SIZEOF_FLOAT, dataIn[6],
                GL3.GL_STATIC_DRAW);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertBufIDs[7]);
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, (long) dataIn[7].capacity() * Buffers.SIZEOF_FLOAT, dataIn[7],
                GL3.GL_STATIC_DRAW);
    }

    static float[] loadVertexData(String filename, int perVertexFloats) {
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

class ShaderLoader {
    static int progID = 0;
    int textVertID = 0;
    int textFragID = 0;

    static int vertexLoc = 0;
    static int colorLoc = 0;
    static int texCoordLoc = 0;
    static int normalLoc = 0;
    static int projectionLoc = 0;
    static int modelviewLoc = 0;
    static int texLoc = 0;

    public void setupShaders(GLAutoDrawable d) {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 3 graphics context

        textVertID = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
        textFragID = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);

        String[] vs = new String[]{
                "#version 140\n" +
                        "\n" +
                        "in vec3 inputPosition;\n" +
                        "in vec4 inputColor;\n" +
                        "in vec2 inputTexCoord;\n" +
                        "in vec3 inputNormal;\n" +
                        "\n" +
                        "uniform mat4 projection;\n" +
                        "uniform mat4 modelview;\n" +
                        "\n" +
                        "out vec3 forFragColor;\n" +
                        "out vec2 forFragTexCoord;\n" +
                        "\n" +
                        "void main(){\n" +
                        "    forFragColor = inputColor.rgb;\n" +
                        "    forFragTexCoord = inputTexCoord;\n" +
                        "    gl_Position =  projection * modelview * vec4(inputPosition, 1.0);\n" +
                        "}\n" };

        String[] fs = new String[]{
                "#version 140\n" +
                        "\n" +
                        "in vec3 forFragColor;\n" +
                        "in vec2 forFragTexCoord;\n" +
                        "out vec4 outputColor;\n" +
                        "\n" +
                        "uniform sampler2D myTexture;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    vec3 textureColor = vec3( texture(myTexture, forFragTexCoord) );\n" +
                        "    outputColor = vec4(forFragColor * textureColor ,1.0);\n" +
                        "}\n" };

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
        texLoc = gl.glGetUniformLocation(progID, "myTexture");
    }

    static void printShaderInfoLog(GLAutoDrawable d, int obj) {
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

    static void printProgramInfoLog(GLAutoDrawable d, int obj) {
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

    public String[] loadShaderSrc(String name) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(name));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[]{sb.toString()};
    }
}