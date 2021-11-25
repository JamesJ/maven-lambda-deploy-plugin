package me.jamesj.lambdadeploy.test;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.jamesj.lambdadeploy.api.ApiFunction;

/**
 * Created by @James on 25/11/2021
 *
 * @author James
 * @since 25/11/2021
 */
@ApiFunction("testingFunction")
public class TestingFunction implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        LambdaLogger logger = context.getLogger();

        // log execution details
        logger.log("ENVIRONMENT VARIABLES: " + gson.toJson(System.getenv()));
        logger.log("CONTEXT: " + gson.toJson(context));
        // process event
        logger.log("EVENT: " + gson.toJson(event));
        logger.log("EVENT TYPE: " + event.getClass().toString());
        return APIGatewayV2HTTPResponse.builder().withBody("Hello world!").withStatusCode(200).build();
    }
}
