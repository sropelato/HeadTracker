package ch.zhaw.headtracker.algorithm;

import ch.zhaw.headtracker.image.Image;
import ch.zhaw.headtracker.image.*;
import java.awt.*;

import static ch.zhaw.headtracker.algorithm.ControlPanel.*;

public final class Algorithm2 implements AlgorithmRunner.Algorithm {
	private final DropdownMenuSetting showImage = new DropdownMenuSetting("Show image", new String[]{ "Background", "Original", "Update mask", "Segmentation mask", "Segmented image" }, 0);
	private final SliderSetting changeThreshold = new SliderSetting("Update threshold", 0, 255, 5);
	private final SliderSetting growRadius = new SliderSetting("Update grow radius", 0, 50, 1);
	private final SliderSetting updateDelay = new SliderSetting("Update delay", 0, 255, 15);
	private final SliderSetting segmentationThreshold = new SliderSetting("Segmentation threshold", 0, 255, 6);
	private final SliderSetting segmentationClosing = new SliderSetting("Segmentation closing radius", 0, 255, 2);
	private final ButtonSetting resetBackground = new ButtonSetting("Reset Background");
	
	private Image background = null;
	private Image lastImage = null;
	private Image steadyCounter = null; // Increased by one on each frame that's identical to the last frame;

	@Override
	public Setting[] getSettings() {
		return new Setting[] { showImage, changeThreshold, growRadius, updateDelay, segmentationClosing, segmentationClosing, resetBackground };
	}

	@Override
	public ImageView.Painter run(final Image image) {
		if (resetBackground.getSignal() || background == null) {
			background = new Image(image.width, image.height);
			steadyCounter = new Image(image.width, image.height);
			lastImage = new Image(image);
		}

		final Image updateMask = differenceMask(image, lastImage, changeThreshold.value);
		
		lastImage = new Image(image);

		ImageUtil.minimum(updateMask, growRadius.value);

		for(int iy = 0; iy < image.height; iy += 1) {
			for(int ix = 0; ix < image.width; ix += 1) {
				int counter = updateMask.getPixel(ix, iy) > 0 ? steadyCounter.getPixel(ix, iy) + 1 : 0;
				
				steadyCounter.setPixel(ix, iy, Math.min(counter, 255));
				
				if (counter > updateDelay.value)
					background.setPixel(ix, iy, image.getPixel(ix, iy));
			}
		}
		
		final Image segmentationMask = differenceMask(image, background, segmentationThreshold.value);
		ImageUtil.minimum(segmentationMask, segmentationClosing.value);
		ImageUtil.maximum(segmentationMask, segmentationClosing.value * 2);
		ImageUtil.minimum(segmentationMask, segmentationClosing.value);
		
		final Image segmentedImage = new Image(image);
		ImageUtil.bitOr(segmentedImage, segmentationMask);
		
		int[] segmentsOnLine = new int[image.height];
		int maxSegmentsOnLine = 0;
		
		for (int iy = 0; iy < image.height; iy += 1) {
			int numSegments = countLineSegments(segmentationMask, iy);
			
			maxSegmentsOnLine = Math.max(maxSegmentsOnLine, numSegments);
			segmentsOnLine[iy] = numSegments;
		}
		
		final int[] segmentsOnLineHistogram = new int[maxSegmentsOnLine + 1];
		
		for (int i : segmentsOnLine)
			segmentsOnLineHistogram[i] += 1;

		return new ImageView.Painter() {
			@Override
			public void draw(Graphics2D g2) {
				g2.setPaint(Color.red);
				g2.setFont(new Font("Lucida Grande", Font.PLAIN, 8));
				
				for (int i = 0; i < segmentsOnLineHistogram.length; i += 1)
					g2.drawString(String.format("%d: %d", i, segmentsOnLineHistogram[i]), 0, i * 10);
			}

			@Override
			protected Image getImage() {
				if (showImage.value == 0)
					return background;
				else if (showImage.value == 1)
					return  image;
				else if (showImage.value == 2)
					return  updateMask;
				else if (showImage.value == 3)
					return  segmentationMask;
				else if (showImage.value == 4)
					return  segmentedImage;
				else
					return  image;
			}
		};
	}

	private static Image differenceMask(Image image1, Image image2, int threshold) {
		Image res = new Image(image1.width, image1.height);

		for(int iy = 0; iy < image1.height; iy += 1) {
			for(int ix = 0; ix < image1.width; ix += 1) {
				int lastPixel = image2.getPixel(ix, iy);
				int imgPixel = image1.getPixel(ix, iy);

				res.setPixel(ix, iy, Math.abs(lastPixel - imgPixel) < threshold);
			}
		}
		return res;
	}
	
	static int countLineSegments(Image image, int y) {
		int count = 0;
		boolean lastInSegment = false;

		for (int ix = 0; ix < image.width; ix += 1) {
			boolean inSegment = image.getPixel(ix, y) == 0;
			
			if (inSegment && !lastInSegment)
				count += 1;
			
			lastInSegment = inSegment;
		}
		
		return count;
	}
}
