
# API Platform Add Subscription Lambda

AWS Lambda used to subscribe a Third Party Application (represented as an AWS API Gateway Usage Plan) to an HMRC API.

Expects a message on an AWS SQS queue in the form:

```json
{
  "applicationName": "third-party-application-name",
  "apiName": "hello--1.0"
}
```

| Field           | Description                                                            |
|-----------------|------------------------------------------------------------------------|
| applicationName | The name of the third party application that is subscribing to the API |
| apiName         | The (normalised) name of the API to subscribe to                       |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
