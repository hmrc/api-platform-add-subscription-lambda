package uk.gov.hmrc.apiplatform.addsubscription

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verifyZeroInteractions, when, verify, times}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions._

class AddSubscriptionHandlerSpec extends WordSpecLike with MockitoSugar with Matchers with JsonMapper {

  trait Setup {
    def buildAddSubscriptionMessage(applicationName: String, apiName: String): SQSMessage = {
      val sqsMessage = new SQSMessage
      sqsMessage.setBody(s"""{ "applicationName": "$applicationName", "apiName": "$apiName" }""")

      sqsMessage
    }

    def buildUsagePlansResponse(usagePlanId: String, applicationName: String): GetUsagePlansResponse =
      GetUsagePlansResponse.builder()
        .items(UsagePlan.builder().id(usagePlanId).name(applicationName).build())
        .build()

    def buildRestApisResponse(restApiId: String, apiName: String): GetRestApisResponse =
      GetRestApisResponse.builder()
        .items(RestApi.builder().id(restApiId).name(apiName).build())
        .build()

    val usagePlanId: String = UUID.randomUUID().toString
    val restAPIId: String = UUID.randomUUID().toString

    val applicationName = "application-1"
    val apiName = "api--1.0"

    val expectedApiStageName = s"$restAPIId:current"

    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val mockContext: Context = mock[Context]
    when(mockContext.getLogger).thenReturn(mock[LambdaLogger])

    val environment: Map[String, String] = Map()

    val addSubscriptionHandler = new AddSubscriptionHandler(mockAPIGatewayClient, environment)
  }

  trait ExistingApplicationAndAPI extends Setup {
    when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildUsagePlansResponse(usagePlanId, applicationName))
    when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(buildRestApisResponse(restAPIId, apiName))
  }

  trait UnknownApplication extends Setup {
    when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(GetUsagePlansResponse.builder().build())
    when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(buildRestApisResponse(restAPIId, apiName))
  }

  trait UnknownAPI extends Setup {
    when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildUsagePlansResponse(usagePlanId, applicationName))
    when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(GetRestApisResponse.builder().build())
  }

  "Add Subscription Handler" should {
    "subscribe Application to API" in new ExistingApplicationAndAPI {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddSubscriptionMessage(applicationName, apiName)))

      val addSubscriptionRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      when(mockAPIGatewayClient.updateUsagePlan(addSubscriptionRequestCaptor.capture())).thenReturn(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      addSubscriptionHandler.handleInput(sqsEvent, mockContext)

      val capturedRequest: UpdateUsagePlanRequest = addSubscriptionRequestCaptor.getValue
      capturedRequest.patchOperations().size() == 1

      val capturedPatchRequest: PatchOperation = capturedRequest.patchOperations().get(0)
      capturedPatchRequest.op() shouldEqual Op.ADD
      capturedPatchRequest.path() shouldEqual "/apiStages"
      capturedPatchRequest.value() shouldEqual expectedApiStageName
    }

    "return if Application name is not recognised" in new UnknownApplication {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddSubscriptionMessage(applicationName, apiName)))

      addSubscriptionHandler.handleInput(sqsEvent, mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])
    }

    "return if API name is not recognised" in new UnknownAPI {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddSubscriptionMessage(applicationName, apiName)))

      addSubscriptionHandler.handleInput(sqsEvent, mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])
    }

    "throw exception if the event has no messages" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List())

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](addSubscriptionHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 0"

      verifyZeroInteractions(mockAPIGatewayClient)
    }

    "throw exception if the event has multiple messages" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(
        List(
          buildAddSubscriptionMessage("application-1", "api-1"),
          buildAddSubscriptionMessage("application-2", "api-2")))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](addSubscriptionHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 2"

      verifyZeroInteractions(mockAPIGatewayClient)
    }
  }
}
