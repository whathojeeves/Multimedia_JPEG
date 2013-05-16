import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Arrays;

import javax.swing.*;


public class assignment2 {

	public static int qFactor = 0;
	public static int latency = 0;
	static JFrame frame = new JFrame();
	static double[] c = {(double)(1/Math.sqrt(2)),1,1,1,1,1,1,1};

	public static int[] xACMapping = {0,1,0,0,1,2,3,2,
		1,0,0,1,2,3,4,5,
		4,3,2,1,0,0,1,2,
		3,4,5,6,7,6,5,4,
		3,2,1,0,1,2,3,4,
		5,6,7,7,6,5,4,3,
		2,3,4,5,6,7,7,6,
		5,4,5,6,7,7,6,7};

	public static int[] yACMapping = {0,0,1,2,1,0,0,1,
		2,3,4,3,2,1,0,0,
		1,2,3,4,5,6,5,4,
		3,2,1,0,0,1,2,3,
		4,5,6,7,7,6,5,4,
		3,2,1,2,3,4,5,6,
		7,7,6,5,4,3,4,5,
		6,7,7,6,5,6,7,7};

	public static int[] regXMapping = {0,1,2,3,4,5,6,7,
		0,1,2,3,4,5,6,7,
		0,1,2,3,4,5,6,7,
		0,1,2,3,4,5,6,7,
		0,1,2,3,4,5,6,7,
		0,1,2,3,4,5,6,7,
		0,1,2,3,4,5,6,7,
		0,1,2,3,4,5,6,7};

	public static int[] regYMapping = {0,0,0,0,0,0,0,0,
		1,1,1,1,1,1,1,1,
		2,2,2,2,2,2,2,2,
		3,3,3,3,3,3,3,3,
		4,4,4,4,4,4,4,4,
		5,5,5,5,5,5,5,5,
		6,6,6,6,6,6,6,6,
		7,7,7,7,7,7,7,7};

	public static int[] intMapMask = {0x80000000,0xC0000000,0xE0000000,0xF0000000,
		0xF8000000,0xFC000000,0xFE000000,0xFF000000,
		0xFF800000,0xFFC00000,0xFFE00000,0xFFF00000,
		0xFFF80000,0xFFFC0000,0xFFFE0000,0xFFFF0000,
		0xFFFF8000,0xFFFFC000,0xFFFFE000,0xFFFFF000,
		0xFFFFF800,0xFFFFFC00,0xFFFFFE00,0xFFFFFF00,
		0xFFFFFF80,0xFFFFFFC0,0xFFFFFFE0,0xFFFFFFF0,
		0xFFFFFFF8,0xFFFFFFFC,0xFFFFFFFE,0xFFFFFFFF};

	public static void main(String[] args) {

		String fileName = args[0];
		int width = 352;//Integer.parseInt(args[1]);
		int height = 288;//Integer.parseInt(args[2]);
		qFactor = Integer.parseInt(args[1]);
		int deliveryMode = Integer.parseInt(args[2]);
		latency = Integer.parseInt(args[3]);
		int x=0,y=0;

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		try {
			File file = new File(fileName);
			InputStream is = new FileInputStream(file);

			long len = file.length();
			byte[] bytes = new byte[(int)len];

			short[][] rBytes = new short[height][width];
			short[][] gBytes = new short[height][width];
			short[][] bBytes = new short[height][width];

			int offset = 0;
			int numRead = 0;
			while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
				offset += numRead;
			}


			int ind = 0;
			for(y = 0; y < height; y++){

				for(x = 0; x < width; x++){

					//byte a = 0;
					rBytes[y][x] = (short) (bytes[ind] & 0xff);
					gBytes[y][x] = (short) (bytes[ind+height*width] & 0xff);
					bBytes[y][x] = (short) (bytes[ind+height*width*2] & 0xff); 
					int pix = 0xff000000 | ((rBytes[y][x] & 0xff) << 16) | ((gBytes[y][x] & 0xff) << 8) | (bBytes[y][x] & 0xff);
					//int pix = ((a << 24) + (r << 16) + (g << 8) + b);
					img.setRGB(x,y,pix);
					ind++;
				}
			}

			/* Encoder : Divide into 8x8 blocks and calculate dct, and get quantized values */
			double[][][] encodedRedPixels = encodeDCT(rBytes, width, height);
			double[][][] encodedGreenPixels = encodeDCT(gBytes, width, height);
			double[][][] encodedBluePixels = encodeDCT(bBytes, width, height);

			JLabel label = new JLabel(new ImageIcon(img));
			frame.getContentPane().add(label, BorderLayout.WEST);
			frame.setVisible(true);

			switch(deliveryMode)
			{
			case 1 :	/* Decoder : Take the encoded blocks, dequantize, run iDCT 
			 *           Sequential decoding
			 */
				short[][][] decodedRedPixels = decodeDCT(encodedRedPixels, width, height);
				short[][][] decodedGreenPixels = decodeDCT(encodedGreenPixels, width, height);
				short[][][] decodedBluePixels = decodeDCT(encodedBluePixels, width, height); 
				doSeqReconstruct(decodedRedPixels, decodedGreenPixels, decodedBluePixels, width, height);

			case 2 :	decodeDCTProgSpec(encodedRedPixels, encodedGreenPixels, encodedBluePixels, width, height);

			case 3 :	decodeDCTProgBit(encodedRedPixels, encodedGreenPixels, encodedBluePixels, width, height);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static double[][][] encodeDCT(short[][] pixelArray, int w, int h)
	{
		int i=0,x=0,y=0,numBlocks=0,j=0;
		short[][] pixelBlock = new short[8][8];
		double[][] dctBlock = new double[8][8];
		double[][][] quantizedValues = new double[(int)(w*h/64)][8][8];

		for(i=0; i<h; i+=8)
		{
			for(j=0;j<w; j+=8)
			{
				for(y=0;y<8;y++)
				{
					for(x=0;x<8;x++)
					{
						pixelBlock[y][x] = pixelArray[y+i][x+j];
					}
				}
				dctBlock = doDCT(pixelBlock);
				quantizedValues[numBlocks++] = quantizeDCT(dctBlock);

			}
		}
		return quantizedValues;
	}

	public static double[][] doDCT(short[][] pixelBlock)
	{
		int x=0,y=0,u=0,v=0;
		double cu=0.0, cv=0.0;
		double[][] resDCT = new double[8][8];

		for(u=0; u<8; u++)
		{
			for(v=0;v<8;v++)
			{
				for(y=0;y<8;y++)
				{
					for(x=0;x<8;x++)
					{
						resDCT[u][v] += (pixelBlock[y][x])*(Math.cos(((2*x + 1)*v*Math.PI)/16))*(Math.cos(((2*y + 1)*u*Math.PI)/16));
					}
				}

				/*cu = 1;
				cv = 1;
				if(u==0)
				{
					cu = (double)(1/(Math.sqrt(2)));
				}
				if(v==0)
				{
					cv = (double)(1/(Math.sqrt(2)));
				}*/

				resDCT[u][v] *= ((c[u]*c[v]*0.25));
			}
		}

		return resDCT;
	}

	public static double[][] quantizeDCT(double[][] pixelBlock)
	{
		double[][] qDCT = new double[8][8];
		int i=0,j=0;
		int mapCount = 0;

		for(i=0;i<8;i++)
		{
			for(j=0;j<8;j++,mapCount++)
			{
				qDCT[yACMapping[mapCount]][xACMapping[mapCount]] = Math.round((pixelBlock[i][j])/(Math.pow(2.0,qFactor)));
			}
		}

		return qDCT;
	}

	public static short[][][] decodeDCT(double[][][] encodedBlocks, int w, int h)
	{
		int totalNumBlocks = (int)(w*h/64);
		int nb=0;
		double[][] deqDCT = new double[8][8];
		short[][][] iDCTBlocks = new short[totalNumBlocks][8][8];

		for(nb=0;nb<totalNumBlocks; nb++)
		{
			deqDCT = dequantizeDCT(encodedBlocks[nb]);
			iDCTBlocks[nb] = doIDCT(deqDCT);//;
		}
		return iDCTBlocks;
	}

	public static void decodeDCTProgSpec(double[][][] rEncodedBlocks, double[][][] gEncodedBlocks, double[][][] bEncodedBlocks, int w, int h)
	{
		int totalNumBlocks = (int)(w*h/64);
		int nb=0,i=0;
		int x=0,y=0,imgX=0,imgY=0;
		double[][] deqDCT = new double[8][8];
		double[][][] rdeqDCT = new double[totalNumBlocks][8][8];
		double[][][] gdeqDCT = new double[totalNumBlocks][8][8];
		double[][][] bdeqDCT = new double[totalNumBlocks][8][8];
		short[][][] riDCTBlocks = new short[totalNumBlocks][8][8];
		short[][][] giDCTBlocks = new short[totalNumBlocks][8][8];
		short[][][] biDCTBlocks = new short[totalNumBlocks][8][8];

		BufferedImage progImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		JLabel pLabel = new JLabel(new ImageIcon(progImage));
		frame.getContentPane().add(pLabel, BorderLayout.EAST);
		frame.pack();

		for(i=0; i<64; i++)
		{
			imgX = 0;
			imgY = 0;
			for(nb=0;nb<totalNumBlocks; nb++)
			{
				deqDCT = dequantizeDCT(rEncodedBlocks[nb]);
				if(i == 0)
					rdeqDCT[nb][0][0] = deqDCT[0][0];
				else
					rdeqDCT[nb][regYMapping[i]][regXMapping[i]] = deqDCT[regYMapping[i]][regXMapping[i]];
				riDCTBlocks[nb] = doIDCT(rdeqDCT[nb]);

				deqDCT = dequantizeDCT(gEncodedBlocks[nb]);
				if(i == 0)
					gdeqDCT[nb][0][0] = deqDCT[0][0];
				else
					gdeqDCT[nb][regYMapping[i]][regXMapping[i]] = deqDCT[regYMapping[i]][regXMapping[i]];
				giDCTBlocks[nb] = doIDCT(gdeqDCT[nb]);

				deqDCT = dequantizeDCT(bEncodedBlocks[nb]);
				if(i == 0)
					bdeqDCT[nb][0][0] = deqDCT[0][0];
				else
					bdeqDCT[nb][regYMapping[i]][regXMapping[i]] = deqDCT[regYMapping[i]][regXMapping[i]];
				biDCTBlocks[nb] = doIDCT(bdeqDCT[nb]);
			}

			for(nb=0; nb< totalNumBlocks; nb++)
			{
				if(imgX== w)
				{
					imgX = 0;
					imgY += 8;
				}
				if(imgY == h)
				{
					break;
				}
				for(y=0; y<8; y++)
				{
					for(x=0; x<8; x++)
					{
						int pix = 0xff000000 | ((riDCTBlocks[nb][y][x] & 0xff) << 16) | ((giDCTBlocks[nb][y][x] & 0xff) << 8) | (biDCTBlocks[nb][y][x] & 0xff);
						progImage.setRGB(imgX+x, imgY+y, pix);
					}
				}
				imgX+= 8;
				try{
					Thread.currentThread();
					Thread.sleep(latency);
				}
				catch(InterruptedException ie){
				}
			}
			pLabel.repaint();
		}
		System.out.println("done");
	}

	public static void decodeDCTProgBit(double[][][] rEncodedBlocks, double[][][] gEncodedBlocks, double[][][] bEncodedBlocks, int w, int h)
	{
		int totalNumBlocks = (int)(w*h/64);
		int nb=0,i=0,j=0;//,qF=qFactor;
		int x=0,y=0,imgX=0,imgY=0;
		double[][] deqDCT = new double[8][8];
		double[][][] rdeqDCT = new double[totalNumBlocks][8][8];
		double[][][] gdeqDCT = new double[totalNumBlocks][8][8];
		double[][][] bdeqDCT = new double[totalNumBlocks][8][8];
		short[][][] riDCTBlocks = new short[totalNumBlocks][8][8];
		short[][][] giDCTBlocks = new short[totalNumBlocks][8][8];
		short[][][] biDCTBlocks = new short[totalNumBlocks][8][8];

		BufferedImage progBitImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		JLabel pbLabel = new JLabel(new ImageIcon(progBitImage));
		frame.getContentPane().add(pbLabel, BorderLayout.EAST);
		frame.pack();

		for(i=23; i<32; i++)
		{
			imgX = 0;
			imgY = 0;
			for(nb=0;nb<totalNumBlocks; nb++)
			{
				deqDCT = dequantizeDCT(rEncodedBlocks[nb]);
				for(j=0;j<64;j++)
				{
					rdeqDCT[nb][regYMapping[j]][regXMapping[j]] = (intMapMask[i] & (int)deqDCT[regYMapping[j]][regXMapping[j]]);
				}
				riDCTBlocks[nb] = doIDCT(rdeqDCT[nb]);

				deqDCT = dequantizeDCT(gEncodedBlocks[nb]);
				for(j=0;j<64;j++)
				{
					gdeqDCT[nb][regYMapping[j]][regXMapping[j]] = (intMapMask[i] & (int)deqDCT[regYMapping[j]][regXMapping[j]]);
				}
				giDCTBlocks[nb] = doIDCT(gdeqDCT[nb]);

				deqDCT = dequantizeDCT(bEncodedBlocks[nb]);
				for(j=0;j<64;j++)
				{
					bdeqDCT[nb][regYMapping[j]][regXMapping[j]] = (intMapMask[i] & (int)deqDCT[regYMapping[j]][regXMapping[j]]);
				}
				biDCTBlocks[nb] = doIDCT(bdeqDCT[nb]);
			}

			for(nb=0; nb< totalNumBlocks; nb++)
			{
				if(imgX== w)
				{
					imgX = 0;
					imgY += 8;
				}
				if(imgY == h)
				{
					break;
				}
				for(y=0; y<8; y++)
				{
					for(x=0; x<8; x++)
					{
						int pix = 0xff000000 | ((riDCTBlocks[nb][y][x] & 0xff) << 16) | ((giDCTBlocks[nb][y][x] & 0xff) << 8) | (biDCTBlocks[nb][y][x] & 0xff);
						progBitImage.setRGB(imgX+x, imgY+y, pix);
					}
				}
				imgX+= 8;
				try{
					Thread.currentThread();
					Thread.sleep(latency);
				}
				catch(InterruptedException ie){
				}
			}
			pbLabel.repaint();
		}
		System.out.println("done");
	}

	public static double[][] dequantizeDCT(double[][] dctBlock)
	{
		int i=0,j=0;
		double[][] dqDCTBlock = new double[8][8];
		int mapCount = 0;

		for(i=0;i<8;i++)
		{
			for(j=0;j<8;j++,mapCount++)
			{
				dqDCTBlock[i][j] = dctBlock[yACMapping[mapCount]][xACMapping[mapCount]]*Math.pow(2.0, qFactor);
			}
		}

		return dqDCTBlock; 
	}

	public static short[][] doIDCT(double[][] dqDCTBlock)
	{
		int x=0,y=0,u=0,v=0;
		double cu=0.0, cv=0.0;
		short[][] iDCT = new short[8][8];
		double idct = 0.0;

		for(y=0; y<8; y++)
		{
			for(x=0;x<8;x++)
			{
				idct = 0.0;
				for(u=0;u<8;u++)
				{
					for(v=0;v<8;v++)
					{
						/*cu = 1;
						cv = 1;
						if(u==0)
						{
							cu = 1/(Math.sqrt(2));
						}
						if(v==0)
						{
							cv = 1/(Math.sqrt(2));
						}*/
						idct += (c[u]*c[v]*(dqDCTBlock[u][v])*(Math.cos(((2*x + 1)*v*Math.PI)/16))*(Math.cos(((2*y + 1)*u*Math.PI)/16)));
					}
				}
				if(Math.round((idct/4)) > 255)
					iDCT[y][x] = 255;
				else if(Math.round((idct/4)) < 0)
					iDCT[y][x] = 0;
				else	
					iDCT[y][x] = (short)Math.round((idct/4));
			}
		}

		return iDCT;
	}

	public static void doSeqReconstruct(short[][][] rPixelBlock, short[][][] gPixelBlock, short[][][] bPixelBlock, int w, int h)
	{
		int x=0,y=0,nb=0;
		int imgX=0,imgY=0;
		int totalNumBlock= (int)(w*h/64);
		BufferedImage seqImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		JLabel sLabel = new JLabel(new ImageIcon(seqImage));
		frame.getContentPane().add(sLabel, BorderLayout.EAST);
		frame.pack();
		frame.setVisible(true);
		for(nb=0; nb<totalNumBlock; nb++)
		{
			if(imgX== w)
			{
				imgX = 0;
				imgY += 8;
			}
			if(imgY == h)
			{
				break;
			}
			for(y=0; y<8; y++)
			{
				for(x=0; x<8; x++)
				{
					int pix = 0xff000000 | ((rPixelBlock[nb][y][x] & 0xff) << 16) | ((gPixelBlock[nb][y][x] & 0xff) << 8) | (bPixelBlock[nb][y][x] & 0xff);
					seqImage.setRGB(imgX+x, imgY+y, pix);
				}
			}
			imgX+= 8;
			try{
				Thread.currentThread();
				Thread.sleep(latency);
			}
			catch(InterruptedException ie){
			}

			sLabel.setIcon(new ImageIcon(seqImage));
		}
	}
}
