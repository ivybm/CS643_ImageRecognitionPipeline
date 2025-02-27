package com.aws.cloud;

import org.apache.commons.io.IOUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FaceRecognition {
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/318788690164/ImageRecognition.fifo";
    private static final String BUCKET_BASE_URL = "https://cs643-sp25-project1.s3.amazonaws.com/";
    private static final int TOTAL_IMAGES = 12;

    public static void main(String[] args) {

        String sqsGroupId = generateRandomString("group", 1000, 9999);

        Random random = new Random();
        List<String> imageUrls = IntStream.generate(() -> random.nextInt(TOTAL_IMAGES) + 1) // Random numbers 1-12
                .distinct() // Ensure unique numbers
                .limit(10) // Get `count` number of images
                .mapToObj(num -> BUCKET_BASE_URL + num + ".jpg") // Append .jpg to numbers
                .collect(Collectors.toList());

        RekognitionClient rekognitionClient = RekognitionClient.create();

        SqsClient sqsClient = SqsClient.create();

        for (String imageUrl : imageUrls) {
            try {
                System.out.println("Processing image: " + imageUrl);
                byte[] imageBytes = fetchImage(imageUrl);

                if (imageBytes != null) {
                    boolean faceDetected = detectFace(rekognitionClient, imageBytes);
                    if (faceDetected) {
                        String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                        sendMessageToSQS(sqsClient, fileName, sqsGroupId);
                    }
                }

            } catch (Exception e) {
                System.err.println("Error processing image: " + imageUrl + " | " + e.getMessage());
            }
        }
        // Signal to Instance B that no more images will be processed
        sendMessageToSQS(sqsClient, "-1", sqsGroupId);

        rekognitionClient.close();
        sqsClient.close();
    }

    private static byte[] fetchImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Java HTTP Client");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        if (connection.getResponseCode() == 200) {
            try (InputStream inputStream = connection.getInputStream()) {
                return IOUtils.toByteArray(inputStream);
            }
        } else {
            System.err.println("Failed to fetch image: " + imageUrl + " | HTTP Status: " + connection.getResponseCode());
            return null;
        }
    }

    private static boolean detectFace(RekognitionClient rekognitionClient, byte[] imageBytes) {
        Image image = Image.builder()
                .bytes(SdkBytes.fromByteBuffer(ByteBuffer.wrap(imageBytes)))
                .build();

        DetectFacesRequest request = DetectFacesRequest.builder()
                .image(image)
                .attributes(Attribute.ALL)
                .build();

        DetectFacesResponse response = rekognitionClient.detectFaces(request);
        for (FaceDetail face : response.faceDetails()) {
            if (face.confidence() > 75) {
                System.out.println("Face detected with confidence: " + face.confidence());
                return true;
            }
        }
        return false;
    }

    private static void sendMessageToSQS(SqsClient sqsClient, String message, String groupId) {
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(SQS_QUEUE_URL)
                .messageBody(message)
                .messageGroupId(groupId)
                .build();

        sqsClient.sendMessage(sendMsgRequest);
        System.out.println("Message sent to SQS: " + message);
    }

    public static String generateRandomString(String prefix, int minValue, int maxValue) {
        Random random = new Random();
        int randomNumber = random.nextInt((maxValue - minValue) + 1) + minValue;
        return prefix + randomNumber;
    }
}
