# Java Hello World

This is a simple example showing how to use the Tink Java AWS KMS integration
with Maven. To build and run this example:

```shell
git clone https://github.com/tink-crypto/tink-java-awskms
cd tink-java-awskms
# Use your credentials and key URI here.
readonly CREDENTIALS_FILE_PATH="testdata/aws/credentials.cred"
readonly MASTER_KEY_URI="aws-kms://arn:aws:kms:us-east-2:235739564943:key/
3ee50705-5a82-4f5b-9753-05c4f473922f"
mvn package -f examples/maven/pom.xml
mvn exec:java -f examples/maven/pom.xml \
  -Dexec.args="keyset.json ${CREDENTIALS_FILE_PATH} ${MASTER_KEY_URI}" \
  && echo "OK!"
```
