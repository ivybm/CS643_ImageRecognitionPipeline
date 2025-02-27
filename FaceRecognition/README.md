## AWS Environment Setup
1. Go to AWS Learner Lab.
2. Start Lab.
3. Once lab has started, click on "AWS Details" then "Download PEM file". 
4. Move the downloaded "labsuser.pem" file to a local directory of your choice.
5. Your key must not be publicly viewable for SSH to work. Use this command:
    `chmod 400 labsuser.pem`
6. From "AWS Details", click on "Show" button next to "AWS CLI". Take note of the following values:
    * aws_access_key_id
    * aws_secret_access_key
    * aws_session_token
7. Click on AWS to access the Console in another tab of the browser.
8. In the AWS Console, launch an EC2 instance using Amazon Linux AMI. 
   * Name this instance as "Instance A".
   * Take note of the instance's "Public IPv4 address". 
9. From your local machine's terminal, you can do SSH access to "Instance A" by running this command:
`ssh -i labsuser.pem ec2-user@<public-ipv4-address>`
10. Install Java 23 in "Instance A" by running this command:
`sudo yum install java-23-amazon-corretto-headless`
11. Verify that Java 23 is successfully installed by running `java -version`.
12. Back in the AWS Console, create a FIFO SQS queue with the following Configuration:
    * Name: ImageRecognition.fifo
    * Visibility timeout: 1 Hour
    * Enable "Content-based deduplication"
    * Enable "High throughput FIFO queue (recommended)"
13. Keep everything else default.
14. Click "Create queue" button.
15. Take note of the URL associated with the SQS queue as it's used as a constant in the app.

## Create a Fat JAR for the App
1. Run the following commands in the app's root directory.
    * `mvn clean install`
    * `mvn clean package`
2. Securely transfer the JAR file to "Instance A" by running the following command:
`scp -i labsuser.pem target/face-recognition-1.0-SNAPSHOT.jar ec2-user@<public-ipv4-address>`
3. In your local machine's terminal that has SSH access to "Instance A", set environment variables:
    * `export AWS_REGION=us-east-1`
    * `export AWS_ACCESS_KEY_ID=<aws_access_key_id>`
    * `export AWS_SECRET_ACCESS_KEY=<aws_secret_access_key>`
    * `export AWS_SESSION_TOKEN=<aws_session_token>`
4. Now it's time to run the app using the JAR:
`java -jar face-recognition-1.0-SNAPSHOT.jar`
5. You should see the following logs for each image processed:
    * Processing image: https://cs643-sp25-project1.s3.amazonaws.com/<image_filename>
    * If the image has a face detected with confidence greater than 75%:
        * Face detected with confidence: <confidence_score>
        * Message sent to SQS: <image_filename>