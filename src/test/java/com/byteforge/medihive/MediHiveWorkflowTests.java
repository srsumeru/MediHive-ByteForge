package com.byteforge.medihive;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional class MediHiveWorkflowTests {
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
 private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b,String username,String role){return b.with(user(username).roles(role));}
 private JsonNode getJson(String url,String username,String role) throws Exception{return mapper.readTree(mvc.perform(as(get(url),username,role)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 private JsonNode postJson(String url,String body,String username,String role) throws Exception {
  var b=as(post(url).with(csrf()),username,role);if(body!=null)b.contentType(MediaType.APPLICATION_JSON).content(body);
  return mapper.readTree(mvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 private int medicineQuantity(JsonNode inventory,long medicineId){
  for(JsonNode item:inventory)if(item.get("medicineId").asLong()==medicineId)return item.get("quantity").asInt();
  throw new AssertionError("Requested medicine absent from inventory");
 }
 private MockHttpServletRequestBuilder stockEdit(int id){return put("/api/inventory/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
  .content("{\"quantity\":500,\"dailyConsumption\":2,\"reorderLevel\":40,\"expiryDate\":\"2028-01-01\"}");}
 @Test void fiveIndependentLoginsAndInventoryEditing() throws Exception {
  mvc.perform(get("/api/me")).andExpect(status().is3xxRedirection());
  for(String[] account:new String[][]{{"warehouse_admin","HiveWH!2026"},{"unity_admin","UnityH!2026"},
      {"city_hospital_admin","CityH!2026"},{"citycare_admin","CityCareP!2026"},{"mediplus_admin","MediPlusP!2026"}}){
   mvc.perform(formLogin().user(account[0]).password(account[1])).andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(account[0]));
   mvc.perform(formLogin().user(account[0]).password("wrong-password")).andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
  }
  assertEquals("Unity Hospital",getJson("/api/me","unity_admin","HOSPITAL").get("facilityName").asText());
  mvc.perform(as(get("/api/dashboard/3"),"unity_admin","HOSPITAL")).andExpect(status().isForbidden());
  mvc.perform(as(get("/api/dashboard/5"),"citycare_admin","PHARMACY")).andExpect(status().isForbidden());
  mvc.perform(as(stockEdit(6),"unity_admin","HOSPITAL")).andExpect(status().isForbidden());
  mvc.perform(as(stockEdit(16),"citycare_admin","PHARMACY")).andExpect(status().isForbidden());
  mvc.perform(as(stockEdit(6),"warehouse_admin","WAREHOUSE")).andExpect(status().isForbidden());
  mvc.perform(as(stockEdit(1),"warehouse_admin","WAREHOUSE")).andExpect(status().isOk());
 }
 @Test void onlyOwningFacilityCanRequestAndHandleOutgoingStock() throws Exception {
  mvc.perform(as(post("/api/requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
    .content("{\"requesterId\":3,\"medicineId\":1,\"quantity\":30,\"priority\":\"HIGH\"}"),"unity_admin","HOSPITAL"))
    .andExpect(status().isForbidden());
  JsonNode high=postJson("/api/requests","{\"requesterId\":4,\"medicineId\":1,\"quantity\":30,\"priority\":\"HIGH\"}","citycare_admin","PHARMACY");
  assertEquals(1,high.get("allocations").get(0).get("sourceId").asInt());
  JsonNode seeded=getJson("/api/requests?facilityId=2","unity_admin","HOSPITAL").get(0);
  long cityAllocation=seeded.get("allocations").get(1).get("id").asLong();
  mvc.perform(as(post("/api/allocations/"+cityAllocation+"/approve").with(csrf()).contentType(MediaType.APPLICATION_JSON)
    .content("{\"quantity\":40}"),"unity_admin","HOSPITAL")).andExpect(status().isForbidden());
  mvc.perform(as(post("/api/allocations/"+cityAllocation+"/approve").with(csrf()).contentType(MediaType.APPLICATION_JSON)
    .content("{\"quantity\":40}"),"citycare_admin","PHARMACY")).andExpect(status().isForbidden());
  JsonNode approved=postJson("/api/allocations/"+cityAllocation+"/approve","{\"quantity\":40}","city_hospital_admin","HOSPITAL");
  assertEquals(40,approved.get("approvedQuantity").asInt());
  mvc.perform(as(post("/api/allocations/"+cityAllocation+"/dispatch").with(csrf()),"unity_admin","HOSPITAL"))
    .andExpect(status().isForbidden());
 }
 @Test void warehouseSeesRegionalDispatchAndOnlyRequesterConfirmsDelivery() throws Exception {
  JsonNode seeded=getJson("/api/requests?facilityId=2","unity_admin","HOSPITAL").get(0);
  long warehouseAllocation=seeded.get("allocations").get(0).get("id").asLong();
  long cityAllocation=seeded.get("allocations").get(1).get("id").asLong();
  postJson("/api/allocations/"+warehouseAllocation+"/approve","{\"quantity\":60}","warehouse_admin","WAREHOUSE");
  postJson("/api/allocations/"+cityAllocation+"/approve","{\"quantity\":40}","city_hospital_admin","HOSPITAL");
  int before=medicineQuantity(getJson("/api/inventory?facilityId=3","city_hospital_admin","HOSPITAL"),seeded.get("medicineId").asLong());
  postJson("/api/allocations/"+cityAllocation+"/dispatch",null,"city_hospital_admin","HOSPITAL");
  int after=medicineQuantity(getJson("/api/inventory?facilityId=3","city_hospital_admin","HOSPITAL"),seeded.get("medicineId").asLong());assertEquals(before-40,after);
  JsonNode warehouseView=getJson("/api/requests?facilityId=1","warehouse_admin","WAREHOUSE");
  JsonNode sameRequest=null;for(JsonNode item:warehouseView)if(item.get("id").asLong()==seeded.get("id").asLong())sameRequest=item;
  assertNotNull(sameRequest);assertEquals("DISPATCHED",sameRequest.get("allocations").get(1).get("state").asText());
  mvc.perform(as(post("/api/allocations/"+cityAllocation+"/deliver").with(csrf()),"city_hospital_admin","HOSPITAL"))
    .andExpect(status().isForbidden());
  JsonNode received=postJson("/api/allocations/"+cityAllocation+"/deliver",null,"unity_admin","HOSPITAL");
  assertEquals(60,received.get("remainingQuantity").asInt());
  mvc.perform(as(post("/api/allocations/"+cityAllocation+"/deliver").with(csrf()),"unity_admin","HOSPITAL"))
    .andExpect(status().isBadRequest());
 }
 @Test void unsafeApprovalIsRejected() throws Exception {
  JsonNode r=postJson("/api/requests","{\"requesterId\":4,\"medicineId\":5,\"quantity\":900,\"priority\":\"HIGH\"}","citycare_admin","PHARMACY");
  long id=r.get("allocations").get(0).get("id").asLong();
  mvc.perform(as(post("/api/allocations/"+id+"/approve").with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"quantity\":900}"),"warehouse_admin","WAREHOUSE")).andExpect(status().isBadRequest());
 }
}
