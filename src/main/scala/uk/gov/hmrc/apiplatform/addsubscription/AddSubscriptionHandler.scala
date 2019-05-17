package uk.gov.hmrc.apiplatform.addsubscription

import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{NotFoundException, Op, PatchOperation, UpdateUsagePlanRequest}
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.AwsIdRetriever
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.SqsHandler

class AddSubscriptionHandler(override val apiGatewayClient: ApiGatewayClient, environment: Map[String, String]) extends SqsHandler with AwsIdRetriever {

  def this() {
    this(awsApiGatewayClient, sys.env)
  }

  override def handleInput(input: SQSEvent, context: Context): Unit = {
    val logger: LambdaLogger = context.getLogger
    if (input.getRecords.size != 1) {
      throw new IllegalArgumentException(s"Invalid number of records: ${input.getRecords.size}")
    }

    val addSubscriptionRequest: AddSubscriptionRequest = fromJson[AddSubscriptionRequest](input.getRecords.get(0).getBody)

    logger.log(s"Attempting to subscribe Application [${addSubscriptionRequest.applicationName}] to API [${addSubscriptionRequest.apiName}]")

    val identifiers: Option[(String, String)] = for {
      usagePlanId <- getAwsUsagePlanIdByApplicationName (addSubscriptionRequest.applicationName)
      restApiId <- getAwsRestApiIdByApiName(addSubscriptionRequest.apiName, logger)
    } yield (usagePlanId, restApiId)

    identifiers match {
      case Some(ids) => updateSubscription(ids._1, ids._2)
      case None =>
        throw NotFoundException.builder()
          .message(s"Unable to subscribe Application [${addSubscriptionRequest.applicationName}] to API [${addSubscriptionRequest.apiName}]")
          .build()
    }
  }

  private def updateSubscription(usagePlanId: String, restApiId: String): Unit = {
      val subscriptionUpdateRequest: UpdateUsagePlanRequest =
        UpdateUsagePlanRequest.builder()
          .usagePlanId(usagePlanId)
          .patchOperations(PatchOperation.builder().op(Op.ADD).path("/apiStages").value(s"$restApiId:current").build())
          .build()
      apiGatewayClient.updateUsagePlan(subscriptionUpdateRequest)
  }
}

case class AddSubscriptionRequest(applicationName: String, apiName: String)