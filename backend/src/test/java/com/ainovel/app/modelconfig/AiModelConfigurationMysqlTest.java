package com.ainovel.app.modelconfig;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.economy.repo.*;
import com.ainovel.app.integration.*;
import com.ainovel.app.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The opt-in runner owns a disposable schema. All gateway and billing traffic is mocked. */
@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","spring.jpa.open-in-view=false",
        "app.ai.text-model-key=model-a","app.ai.text-model-display-name=Model A"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({com.ainovel.app.ai.AiStoredResponseMetadata.class,com.ainovel.app.integration.ExternalServiceProperties.class,
        com.ainovel.app.manuscript.ManuscriptContentService.class,com.ainovel.app.common.JsonColumnCodec.class, AiModelConfigurationService.class,AiQuoteService.class,AiActionPlanner.class,AiModelPolicy.class,
        EconomyService.class,AiService.class,AiModelConfigurationMysqlTest.Beans.class})
class AiModelConfigurationMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        properties.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
    }
    @TestConfiguration static class Beans {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager){return new TransactionTemplate(manager);}
    }
    @MockitoBean com.ainovel.app.world.WorldBindingService worldBindings;
    @MockitoBean com.ainovel.app.worldcontext.WorldContextPreferences worldPreferences;
    @MockitoBean com.ainovel.app.quality.language.LanguageFeature languageFeature;
    @MockitoBean com.ainovel.app.manuscript.context.SceneDraftContextCompiler compiler;
    @MockitoBean com.ainovel.app.worldassistant.WorldAssistantService worldAssistant;
    @MockitoBean com.ainovel.app.manuscript.SceneGenerationService sceneGeneration;
    @MockitoBean com.ainovel.app.assistant.WritingAssistantService writingAssistant;
    @MockitoBean com.ainovel.app.story.OutlineAiService outlineAi;
    @MockitoBean com.ainovel.app.writingagent.WritingAgentService writingAgent;
    @MockitoBean AiGatewayGrpcClient gateway;
    @MockitoBean BillingGrpcClient billing;
    @MockitoBean AiAdmissionGuard admission;
    @Autowired AiModelConfigurationService config;
    @Autowired AiQuoteService quotes;
    @Autowired AiService ai;
    @Autowired EconomyService economy;
    @Autowired AiCreditReservationRepository reservations;
    @Autowired ProjectCreditAccountRepository accounts;
    @Autowired ProjectCreditLedgerRepository ledger;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;
    private static final AtomicLong UID=new AtomicLong(9900000);
    private User user;
    @BeforeEach void setup() {
        when(gateway.listCallerModels()).thenReturn(List.of(model("1","model-a"),model("2","model-b")));
        when(admission.requestHash(any())).thenAnswer(i->AiActionPlanner.hash(i.getArgument(0).toString()));
        when(gateway.complete(anyString(),anyLong(),any(),anyList(),any())).thenReturn(new AiGatewayGrpcClient.ChatResult("retained result","model-a",100,100,0));
        config.sync();
        user=tx.execute(s->{var u=new User();String name="model-config-"+UUID.randomUUID();u.setUsername(name);u.setEmail(name+"@example.invalid");u.setPasswordHash("fixture");u.setRemoteUid(UID.incrementAndGet());u.setCredits(100);em.persist(u);em.flush();return u;});
        setPrice("FIXED",3L,null,null,true);
    }
    private AiModelConfigurationService.CatalogModel model(String id,String key) {
        return new AiModelConfigurationService.CatalogModel(id,key,key,"fixture",true,true,false,AiModelConfigurationService.Thinking.unknown(),null);
    }
    private AiModelConfigurationService.FeatureView setPrice(String mode,Long fixed,BigDecimal input,BigDecimal output,boolean paid) {
        var before=config.feature("generic.chat",user);String id=UUID.randomUUID().toString();
        var option=new AiModelConfigurationService.OptionInput(id,"model-a","Custom visible name","DEFAULT",null,mode,fixed,input,output);
        return config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(before.revision(),paid,id,List.of(option)));
    }
    private AiQuoteService.View quote() {return quotes.create(user,new AiQuoteService.Request("/v1/ai/chat",json.valueToTree(Map.of("messages",List.of(Map.of("role","user","content","hello")))),Map.of()));}
    private AiSelectionContext.Snapshot accept(AiQuoteService.View q,String key) {
        return quotes.accept(user,"/v1/ai/chat",json.valueToTree(Map.of("messages",List.of(Map.of("role","user","content","hello")))),q.id(),key);
    }
    private AiChatRequest request(String content) {return new AiChatRequest(List.of(new AiChatRequest.Message("user",content)),null,null).forFeature("generic.chat");}
    @Test void newWorldEntryStepsRemainUnconfiguredUntilExplicitlySaved() {
        for(String key:List.of("world.entry.generate","world.entry.refine","world_assistant.discuss.plan","world_assistant.discuss.final","world_assistant.propose.plan","world_assistant.propose.final","world_assistant.refine.plan","world_assistant.refine.final","world.overview.integrate")) {
            assertThat(config.feature(key,user).options()).isEmpty();
            assertThat(config.feature(key,user).defaultOptionId()).isNull();
            String id=UUID.randomUUID().toString();
            var option=new AiModelConfigurationService.OptionInput(id,"model-a","条目模型","DEFAULT",null,"FIXED",2L,null,null);
            var saved=config.update(key,new AiModelConfigurationService.FeatureUpdate(0,true,id,List.of(option)));
            assertThat(saved.revision()).isEqualTo(1);assertThat(saved.options()).hasSize(1);
            assertThat(saved.defaultOptionId()).isEqualTo(id);
            jdbc.update("delete from ai_model_options where task_type=?",key);
            jdbc.update("delete from task_model_routing where task_type=?",key);
        }
    }
    @Test void paidRequiresAQuoteAndAcceptedSelectionSurvivesPriceChangeAndExpiry() {
        assertThatThrownBy(()->quotes.accept(user,"/v1/ai/chat",json.createObjectNode(),null,"key")).hasMessage("AI_QUOTE_REQUIRED");
        var q=quote();var snapshot=accept(q,"same-request");
        setPrice("FIXED",19L,null,null,true);
        jdbc.update("update ai_model_quotes set expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(1)),q.id());
        assertThat(accept(q,"same-request")).isEqualTo(snapshot);
        assertThat(snapshot.selections().get("generic.chat").fixedCredits()).isEqualTo(3L);
        assertThatThrownBy(()->accept(q,"different-request")).hasMessage("AI_QUOTE_ALREADY_USED");
    }
    @Test void unacceptedQuotesRejectChangedPricesExpiredTimeDifferentInputAndDifferentUsers() {
        var q=quote();setPrice("FIXED",8L,null,null,true);
        assertThatThrownBy(()->accept(q,"request")).hasMessage("AI_QUOTE_INPUT_CHANGED");
        var expired=quote();jdbc.update("update ai_model_quotes set expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(1)),expired.id());
        assertThatThrownBy(()->accept(expired,"request")).hasMessage("AI_QUOTE_EXPIRED");
        var changed=quote();assertThatThrownBy(()->quotes.accept(user,"/v1/ai/chat",json.valueToTree(Map.of("messages",List.of())),changed.id(),"request")).hasMessage("AI_QUOTE_INPUT_CHANGED");
        var other=new User();other.setId(UUID.randomUUID());assertThatThrownBy(()->quotes.accept(other,"/v1/ai/chat",json.createObjectNode(),changed.id(),"request")).hasMessage("AI_QUOTE_INVALID");
    }
    @Test void syncPreservesRetailOptionsWhileEmptyAndFailedCatalogsDoNotChooseOtherModels() {
        var before=config.feature("generic.chat",user);
        when(gateway.listCallerModels()).thenThrow(new IllegalStateException("upstream unavailable"));
        assertThatThrownBy(config::sync).hasMessage("AI_CATALOG_SYNC_FAILED");
        assertThat(config.feature("generic.chat",user).options()).isEqualTo(before.options());assertThat(config.available("model-a")).isTrue();
        doReturn(List.of()).when(gateway).listCallerModels();config.sync();
        var after=config.feature("generic.chat",user);
        assertThat(after.options()).extracting(AiModelSelection::label).containsExactly("Custom visible name");
        assertThat(after.unavailableOptions()).containsExactly(before.defaultOptionId());
        assertThatThrownBy(this::quote).hasMessage("AI_OPTION_UNAVAILABLE");
    }
    @Test void freeNeedsExactlyOnePlanAndDoesNotReserveOrDebitEvenAtZeroBalance() {
        var feature=setPrice("TOKENS",null,null,null,false);
        jdbc.update("update users set credits=0 where id=?",AiModelConfigurationService.bytes(user.getId()));
        var snapshot=quotes.accept(user,"/v1/ai/chat",json.createObjectNode(),null,"free-request");
        try(var ignored=AiSelectionContext.install(snapshot)) {
            var result=ai.chatWithIdempotency(user,request("free"),"free-call");assertThat(result.usage().cost()).isZero();
        }
        assertThat(economy.projectBalance(user)).isZero();assertThat(jdbc.queryForObject("select count(*) from project_credit_ledger where user_id=?",Long.class,AiModelConfigurationService.bytes(user.getId()))).isZero();
        var paid=new AiModelConfigurationService.OptionInput(UUID.randomUUID().toString(),"model-a","second","DEFAULT",null,"FIXED",1L,null,null);
        assertThatThrownBy(()->config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(feature.revision(),false,feature.defaultOptionId(),List.of(paid,paid)))).hasMessage("AI_OPTIONS_INVALID");
        var stored=reservations.findAll().stream().filter(r->r.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
        assertThat(stored.getReservedAmount()).isZero();assertThat(stored.getSettledAmount()).isZero();assertThat(stored.getStatus()).isEqualTo(AiCreditReservation.Status.COMPLETED);
    }
    @Test void fixedCallsReplayWithoutAnotherModelInvocationAndOnlyChargeOnce() {
        var snapshot=accept(quote(),"fixed-request");
        try(var ignored=AiSelectionContext.install(snapshot)) {
            assertThat(ai.chatWithIdempotency(user,request("hello"),"fixed-call").usage().cost()).isEqualTo(3);
            assertThat(ai.chatWithIdempotency(user,request("hello"),"fixed-call").content()).isEqualTo("retained result");
        }
        assertThat(economy.projectBalance(user)).isEqualTo(97);
        verify(gateway,times(1)).complete(anyString(),anyLong(),any(),anyList(),any());
        var stored=reservations.findAll().stream().filter(r->r.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
        assertThat(stored.getActualModelKey()).isEqualTo("model-a");assertThat(stored.getFrozenRequestJson()).contains("hello");assertThat(stored.getSelectionJson()).contains("Custom visible name");
    }
    @Test void tokenResultRemainsReadyUntilTopUpAndSettlementNeverCallsTheModelAgain() {
        setPrice("TOKENS",null,BigDecimal.ONE,BigDecimal.ONE,true);
        jdbc.update("update users set credits=10 where id=?",AiModelConfigurationService.bytes(user.getId()));
        when(gateway.complete(anyString(),anyLong(),any(),anyList(),any())).thenReturn(new AiGatewayGrpcClient.ChatResult("saved expensive result","model-a",20000,20000,500));
        var snapshot=accept(quote(),"token-request");
        try(var ignored=AiSelectionContext.install(snapshot)) {
            assertThatThrownBy(()->ai.chatWithIdempotency(user,request("hello"),"token-request")).isInstanceOf(AiPaymentRequiredException.class);
        }
        var stored=reservations.findAll().stream().filter(r->r.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AiCreditReservation.Status.RESULT_READY);assertThat(stored.getResultContent()).isEqualTo("saved expensive result");
        assertThatThrownBy(this::quote).isInstanceOf(AiPaymentRequiredException.class);
        assertThat(quotes.pending(user)).singleElement().satisfies(p->{assertThat(p.quoteId()).isEqualTo(snapshot.identity());assertThat(p.target()).isEqualTo("/v1/ai/chat");assertThat(p.requestKey()).isEqualTo("token-request");});
        jdbc.update("update project_credit_accounts set balance=balance+100 where user_id=?",AiModelConfigurationService.bytes(user.getId()));
        setPrice("FIXED",1L,null,null,true);
        try(var ignored=AiSelectionContext.install(snapshot)) {assertThat(ai.chatWithIdempotency(user,request("hello"),"token-request").usage().cost()).isEqualTo(40);}
        assertThat(economy.projectBalance(user)).isEqualTo(70);verify(gateway,times(1)).complete(anyString(),anyLong(),any(),anyList(),any());
        assertThat(quotes.pending(user)).isEmpty();
    }
    @Test void aConfirmedLaterBusinessFailureDoesNotRefundPriorSuccessfulCalls() {
        var snapshot=accept(quote(),"partial-request");
        when(gateway.complete(anyString(),anyLong(),any(),anyList(),any())).thenReturn(new AiGatewayGrpcClient.ChatResult("first result","model-a",10,10,0))
                .thenThrow(new AiRequestNotSentException(org.springframework.http.HttpStatus.CONFLICT,"AI_MODEL_UNAVAILABLE"));
        try(var ignored=AiSelectionContext.install(snapshot)) {
            assertThat(ai.chat(user,request("first")).usage().cost()).isEqualTo(3);
            assertThatThrownBy(()->ai.chat(user,request("second"))).isInstanceOf(AiRequestNotSentException.class);
        }
        assertThat(economy.projectBalance(user)).isEqualTo(97);
        assertThat(reservations.findAll().stream().filter(r->r.getUser().getId().equals(user.getId()))).extracting(AiCreditReservation::getStatus)
                .containsExactlyInAnyOrder(AiCreditReservation.Status.COMPLETED,AiCreditReservation.Status.RELEASED);
    }
    @Test void quoteRejectsChangedBusinessDataBeforeSubmission() {
        var story=tx.execute(s->{var v=new com.ainovel.app.story.model.Story();v.setUser(user);v.setTitle("Original");v.setSynopsis("Before");v.setStatus("draft");em.persist(v);em.flush();return v;});
        String target="/v1/story-cards/"+story.getId()+"/refine";var body=json.valueToTree(Map.of("text","original"));
        var q=quotes.create(user,new AiQuoteService.Request(target,body,Map.of()));
        jdbc.update("update stories set synopsis='Changed' where id=?",AiModelConfigurationService.bytes(story.getId()));
        assertThatThrownBy(()->quotes.accept(user,target,body,q.id(),"business-request")).hasMessage("AI_QUOTE_INPUT_CHANGED");
    }
    @Test void sameModelCanHaveDifferentPlansAndADeletedPreferenceCannotSilentlyFallBack() {
        var f=config.feature("generic.chat",user);String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        var first=new AiModelConfigurationService.OptionInput(a,"model-a","Fast","DEFAULT",null,"FIXED",1L,null,null);
        var second=new AiModelConfigurationService.OptionInput(b,"model-a","Careful","DEFAULT",null,"FIXED",5L,null,null);
        var updated=config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(f.revision(),true,a,List.of(first,second)));
        config.preference(user,"generic.chat",b);assertThat(quote().steps().getFirst().selected().label()).isEqualTo("Careful");
        config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(updated.revision(),true,a,List.of(first)));
        assertThatThrownBy(this::quote).hasMessage("AI_PREFERENCE_UNAVAILABLE");
        config.preference(user,"generic.chat",a);assertThat(quote().steps().getFirst().selected().label()).isEqualTo("Fast");
    }
    @Test void newUnknownThinkingOptionsAreRejectedAndStaleWritesDoNotOverwritePricing() {
        var f=config.feature("generic.chat",user);String id=UUID.randomUUID().toString();
        var on=new AiModelConfigurationService.OptionInput(id,"model-a","Unsupported","ON","HIGH","FIXED",1L,null,null);
        assertThatThrownBy(()->config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(f.revision(),true,id,List.of(on)))).hasMessage("AI_THINKING_UNSUPPORTED");
        setPrice("FIXED",9L,null,null,true);
        assertThatThrownBy(()->config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(f.revision(),true,id,List.of(on)))).hasMessage("AI_CONFIGURATION_CHANGED");
    }
    @Test void concurrentSameCallCannotDoubleReserveOrDoubleInvoke() throws Exception {
        var snapshot=accept(quote(),"concurrent-request");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(gateway.complete(anyString(),anyLong(),any(),anyList(),any())).thenAnswer(i->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return new AiGatewayGrpcClient.ChatResult("one result","model-a",1,1,0);});
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->{try(var ignored=AiSelectionContext.install(snapshot)){return ai.chatWithIdempotency(user,request("hello"),"concurrent-call");}});
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            try(var ignored=AiSelectionContext.install(snapshot)){assertThatThrownBy(()->ai.chatWithIdempotency(user,request("hello"),"concurrent-call")).hasMessageContaining("正在处理中");}
            release.countDown();assertThat(first.get(5,TimeUnit.SECONDS).content()).isEqualTo("one result");
        } finally {release.countDown();}
        assertThat(economy.projectBalance(user)).isEqualTo(97);verify(gateway,times(1)).complete(anyString(),anyLong(),any(),anyList(),any());
    }
    @Test void freeRetriesRetainTheOriginalPlanAfterAdminEnablesBilling() {
        setPrice("TOKENS",null,null,null,false);
        var body=json.createObjectNode();var original=quotes.accept(user,"/v1/ai/chat",body,null,"free-retry");
        setPrice("FIXED",9L,null,null,true);
        assertThat(quotes.accept(user,"/v1/ai/chat",body,null,"free-retry")).isEqualTo(original);
        assertThatThrownBy(()->quotes.accept(user,"/v1/ai/chat",body,null,"new-request")).hasMessage("AI_QUOTE_REQUIRED");
        try(var ignored=AiSelectionContext.install(original)) {assertThat(ai.chatWithIdempotency(user,request("hello"),"free-retry").usage().cost()).isZero();}
        assertThat(economy.projectBalance(user)).isEqualTo(100);
    }
    @Test void oneModelCanOfferSeparateThinkingLevelsAndUnsupportedLevelsAreRejected() {
        var thinking=new AiModelConfigurationService.Thinking(true,true,true,List.of(new AiModelConfigurationService.ThinkingOption("LOW","Low"),new AiModelConfigurationService.ThinkingOption("HIGH","High")),null,null);
        when(gateway.listCallerModels()).thenReturn(List.of(new AiModelConfigurationService.CatalogModel("1","model-a","Model A","fixture",true,false,false,thinking,null)));
        config.sync();var f=config.feature("generic.chat",user);String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        var low=new AiModelConfigurationService.OptionInput(a,"model-a","Low price","ON","LOW","FIXED",2L,null,null);
        var high=new AiModelConfigurationService.OptionInput(b,"model-a","High price","ON","HIGH","TOKENS",null,new BigDecimal("0.123456"),new BigDecimal("0.654321"));
        config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(f.revision(),true,a,List.of(low,high)));
        config.preference(user,"generic.chat",b);var selection=quote().steps().getFirst().selected();
        assertThat(selection.thinkingEffort()).isEqualTo("HIGH");assertThat(selection.modelKey()).isEqualTo("model-a");assertThat(selection.streaming()).isFalse();
        var bad=new AiModelConfigurationService.OptionInput(a,"model-a","Unsupported","ON","MAX","FIXED",2L,null,null);
        assertThatThrownBy(()->config.update("generic.chat",new AiModelConfigurationService.FeatureUpdate(f.revision()+1,true,a,List.of(bad)))).hasMessage("AI_THINKING_UNSUPPORTED");
    }
    @Test void multiStepQuoteUsesTrustedActionAndG2TaskReceiptRetainsBothSelections() {
        var story=tx.execute(s->{var v=new com.ainovel.app.story.model.Story();v.setUser(user);v.setTitle("Story");v.setSynopsis("Input");v.setStatus("draft");em.persist(v);em.flush();return v;});
        String target="/v1/story-cards/"+story.getId()+"/assistant/turns";var body=json.valueToTree(Map.of("intent","DISCUSS","pricingMode","FREE"));
        var q=quotes.create(user,new AiQuoteService.Request(target,body,Map.of()));
        assertThat(q.steps()).extracting(v->v.step().featureKey()).containsExactly("story_assistant.discuss.plan","story_assistant.discuss.final");
        assertThat(q.steps()).allSatisfy(v->{assertThat(v.step().conditional()).isFalse();assertThat(v.selected().billingEnabled()).isTrue();});
        var snapshot=quotes.accept(user,target,body,q.id(),"multi-step");var sample=UUID.randomUUID();
        try(var ignored=AiSelectionContext.install(snapshot)){quotes.freezeTaskSnapshot(user,sample);}
        setPrice("FIXED",99L,null,null,true);
        assertThat(quotes.taskSnapshot(sample)).isEqualTo(snapshot);
    }
    @Test void failedG2SampleRefundsNetReservationAndSettlementDebitsExactlyOnce() {
        var snapshot=accept(quote(),"g2-receipt");UUID sample=UUID.randomUUID();
        try(var ignored=AiSelectionContext.install(snapshot)) {
            ai.chat(user,request("first sample call"),new AiUsageContext("G2_EVALUATION",sample.toString(),"draft"));
            when(gateway.complete(anyString(),anyLong(),any(),anyList(),any())).thenThrow(new AiRequestNotSentException(org.springframework.http.HttpStatus.CONFLICT,"AI_MODEL_UNAVAILABLE"));
            assertThatThrownBy(()->ai.chat(user,request("second call"),new AiUsageContext("G2_EVALUATION",sample.toString(),"revise"))).isInstanceOf(AiRequestNotSentException.class);
        }
        assertThat(economy.projectBalance(user)).isEqualTo(97);
        assertThat(economy.refundFailedEvaluation(user,sample)).isEqualTo(3);
        assertThat(economy.refundFailedEvaluation(user,sample)).isZero();assertThat(economy.projectBalance(user)).isEqualTo(100);
    }

    @Test void unresolvedResultsCannotBeBypassedWithAFreshQuoteAndRequestKey() {
        var q=quote();var snapshot=accept(q,"uncertain-request");
        when(gateway.complete(anyString(),anyLong(),any(),anyList(),any())).thenThrow(new IllegalStateException("transport outcome unknown"));
        try(var ignored=AiSelectionContext.install(snapshot)) {
            assertThatThrownBy(()->ai.chatWithIdempotency(user,request("hello"),"uncertain-request")).isInstanceOf(AiResultUncertainException.class);
        }
        assertThatThrownBy(this::quote).hasMessage("AI_RESULT_RECONCILIATION_REQUIRED");
        assertThat(accept(q,"uncertain-request")).isEqualTo(snapshot);
        try(var ignored=AiSelectionContext.install(snapshot)) {
            assertThatThrownBy(()->ai.chatWithIdempotency(user,request("hello"),"uncertain-request")).isInstanceOf(AiResultUncertainException.class);
        }
        verify(gateway,times(1)).complete(anyString(),anyLong(),any(),anyList(),any());
    }

}
