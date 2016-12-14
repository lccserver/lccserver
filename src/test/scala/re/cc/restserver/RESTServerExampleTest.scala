package re.cc.restserver

import com.moseph.scalsc.utility.BaseTest
import com.twitter.finagle.http.Status._
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import com.twitter.inject.Mockito
import com.twitter.finatra.json.utils.JsonDiffUtil
import com.moseph.scalsc.environment.ResourceProtocolStore
import org.json4s.native.JsonMethods._
import org.json4s._
import com.moseph.scalsc.server.rest._
import org.junit.runner.RunWith

/**
 * Example server operation with JSON. Test class shows the JSON sent (for POST/PUT requests)
 * and the JSON expected after some of the creations
 */
class RESTServerExampleTest extends FeatureTest with Mockito {
  val server = new EmbeddedHttpServer(new InstitutionRESTServer(new ResourceProtocolStore("/protocols")))
  implicit val formats = DefaultFormats
  
  "Server Setup" in {
    //Should start with no institutions
    server.httpGet(
        path="/institutions",
        andExpect = Ok,
        withJsonBody = "[]"
        )
    server.httpPost(
        path="/create_institution",
        postBody = """
          { "wrong_field" : "test_institution" }
          """,
        andExpect = BadRequest
    )
    server.httpPost(
        path="/create_institution",
        postBody = """
          { "name" : "test_institution" }
          """,
        andExpect = Ok,
        withJsonBody = """{ 
          "type":"institution", 
          "path":"http://localhost:8888/institution/user/manager/test_institution" 
          } """
    )
    server.httpGet(
        path="/institutions",
        andExpect = Ok,
        withJsonBody = """
          [{
            "type":"institution",
            "path":"http://localhost:8888/institution/user/manager/test_institution"
            }]
          """
        )
        
    server.httpGet(
        path="/institution/user/manager/test_institution",
        andExpect = Ok,
        withJsonBody = """
          {
            "type":"institution",
            "path":"http://localhost:8888/institution/user/manager/test_institution",
            "interactions":[]
          }
          """
        )
    val inter_resp = server.httpPost(
        path="/institution/create/user/manager/test_institution",
        postBody = """
          {
            "template":
            {
            "protocol_id" : "example-soft",
            "agents": [
              {
                "agent_id":"jimmy",
                "roles" : [ { "role" : "seller" } ],
                "knowledge" : [ "got(camera)", "likes(camels)" ]
              }, 
            ],
            },
            "data" : {}
          }
          """,
        andExpect = Ok
        )
    val institution_path = "http://localhost:8888/interaction/user/manager/test_institution/"
    val interaction_path = (parse( inter_resp.contentString) \\ "path").extract[String]
    // Should be somthing like: http://localhost:8888/interaction/user/manager/test_institution/int3890
    interaction_path should startWith(institution_path)
    val interaction_id = interaction_path.replaceFirst(institution_path,"")
    interaction_id should startWith("int")
    
    //Check that the interaction is there
    server.httpGet(
        path=interaction_path,
        andExpect = Ok,
        withJsonBody = s"""
          {
            "type":"interaction",
            "path" : "http://localhost:8888/interaction/user/manager/test_institution/$interaction_id",
            "agents" : [
              {
                "type" : "agent",
                "path" : "http://localhost:8888/agent/user/manager/test_institution/$interaction_id/jimmy"
              }
            ]
          }

          """
        )
    
     //Make a new agent in the interaction
     server.httpPost(
         path=s"http://localhost:8888/interaction/create/user/manager/test_institution/$interaction_id",
         postBody = """
         {
           "template":{
             "agent_id":"kevin",
             "roles" : [ { "role" : "interested(jimmy)" } ]
           },
           "data" : {}
         }
           """,
         andExpect = Ok,
         withJsonBody = s"""
           {
             "type" : "agent",
             "path":"http://localhost:8888/agent/user/manager/test_institution/$interaction_id/kevin"
           }

           """
         )
    //Check that the new agent is there
    server.httpGet(
        path=interaction_path,
        andExpect = Ok,
        withJsonBody = s"""
          {
            "type":"interaction",
            "path" : "http://localhost:8888/interaction/user/manager/test_institution/$interaction_id",
            "agents" : [
              {
                "type" : "agent",
                "path" : "http://localhost:8888/agent/user/manager/test_institution/$interaction_id/jimmy"
              },
              {
                "type" : "agent",
                "path" : "http://localhost:8888/agent/user/manager/test_institution/$interaction_id/kevin"
              }
            ]
          }

          """
        )
         
        Thread.sleep(1000)
    val jimmy_path = s"http://localhost:8888/agent/user/manager/test_institution/$interaction_id/jimmy"
    val kevin_path = s"http://localhost:8888/agent/user/manager/test_institution/$interaction_id/kevin"
    server.httpGet(
        path=kevin_path,
        andExpect = Ok,
        withJsonBody = s"""
          {
            "type":"agent",
            "path":"$kevin_path",
            "next_steps":["e(want(T), _)"]
          }
          """
            )
    server.httpGet(
        path=jimmy_path,
        andExpect = Ok,
        withJsonBody = s"""
          {
            "type":"agent",
            "path":"$jimmy_path",
            "next_steps":["want(T) <= a( interested(jimmy), I )" ]

          }
          """
            )
            
    //Kevin is waiting for something to fill in e(want(T)) so we'll supply it
    server.httpPost(
        path=s"http://localhost:8888/agent/elicited/user/manager/test_institution/$interaction_id/kevin",
        postBody="""
        {"elicited":"e(want(camera),data(source(\"test_setup\")))"} 
        """,
        andExpect = Ok
        )
        //Wait again to make sure it's run through
    Thread.sleep(1000)

    server.httpGet(
        path=kevin_path,
        andExpect = Ok,
        withJsonBody = s"""
          {
            "type":"agent",
            "path":"$kevin_path",
            "next_steps":[]
          }
          """
            )
    server.httpPost(
        path=s"http://localhost:8888/agent/compute/user/manager/test_institution/$interaction_id/kevin",
        postBody = s""" { "compute":"i(got(T))" } """,
        andExpect = Ok,
        withJsonBody = s"""
          {
            "type":"compute",
            "result":"i(got(camera), source(name(local)))"
          }
          """
            )
  }

}