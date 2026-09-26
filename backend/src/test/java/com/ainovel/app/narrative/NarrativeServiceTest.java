package com.ainovel.app.narrative;

import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.*;
import com.ainovel.app.v2.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.util.*;
import static com.ainovel.app.narrative.NarrativeDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DataJpaTest(showSql = false)
@Import({NarrativeService.class, NarrativeContextService.class, NarrativeInvalidationListener.class, ResourceAccessGuard.class, V2VersionPersistenceService.class,
        JsonColumnCodec.class, NarrativeServiceTest.Beans.class})
class NarrativeServiceTest {
    @TestConfiguration
    @ComponentScan(basePackages="com.ainovel.app.v2", useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.REGEX, pattern="com.ainovel.app.v2.V2Json"))
    static class Beans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean Validator validator() { return new LocalValidatorFactoryBean(); }
        @Bean CurrentUserResolver currentUserResolver() { return mock(CurrentUserResolver.class); }
    }
    @Autowired TestEntityManager em;
    @Autowired NarrativeService service;
    @Autowired NarrativeContextService context;
    @Autowired V2VersionPersistenceService versions;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.beans.factory.config.AutowireCapableBeanFactory beanFactory;
    User user;
    Manuscript manuscript;
    CharacterCard character;
    UUID branch, scene1 = UUID.randomUUID(), scene2 = UUID.randomUUID(), chapter = UUID.randomUUID();
    static final String TEXT = "<p>𠮷😀林青说：桥断了。他准备去偷钥匙。</p><p>林青误以为同伴背叛，昨夜梦见大火。</p>";

    private NarrativeContextDtos.State configure(NarrativeContextDtos.Document document) {
        var s=context.state(user,manuscript.getId(),branch);
        return context.update(user,manuscript.getId(),branch,new NarrativeContextDtos.Update(s.manuscriptVersion(),s.canonRevision(),s.revision(),s.settingsRevision(),true,document),UUID.randomUUID().toString());
    }
    private NarrativeContextDtos.Document document(List<NarrativeContextDtos.Grant> grants,List<NarrativeContextDtos.Entry> entries) {
        return new NarrativeContextDtos.Document(new NarrativeContextDtos.Policy(NarrativeContextDtos.Perspective.LIMITED_THIRD,true,Map.of(scene2,character.getId())),grants,entries);
    }
    private NarrativeContextDtos.Preview preview(NarrativeContextDtos.View view,UUID who) {
        return context.preview(user,manuscript.getId(),branch,scene2,view,who,3500);
    }

    /** Offline request fixtures only: opt-in export never calls a model or writes the development database. */
    @ParameterizedTest
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="h23.exportRequests",matches="true")
    @org.junit.jupiter.params.provider.ValueSource(ints={1,2,3,4,5,6})
    void exportFrozenH2ComparisonRequests(int number) throws Exception {
        var root=java.nio.file.Path.of("../artifacts/h23-quality-20260922");
        var cases=json.readTree(root.resolve("frozen-cases.json").toFile());
        var item=cases.path("acceptance").get(number-1);String id=item.path("id").asText();
        var output=root.resolve("requests/"+id+".json");
        assertFalse(java.nio.file.Files.exists(output),"Frozen requests must not be silently overwritten");
        character.setName(item.path("pov").asText());
        character.setDetails("UNCLASSIFIED_SECRET_"+id);
        manuscript.setSectionsJson(write(Map.of(scene1,"<p>"+item.path("source").asText()+"</p>",scene2,"")));
        var kinds=List.of(Kind.BELIEF,Kind.UTTERANCE,Kind.FACT,Kind.BELIEF,Kind.FACT,Kind.FACT);
        var safe=List.of("许霁相信包裹已经被焚毁", "何岚听陶先说渡口封闭，尚未核实", "顾蘅知道钟声是撤离信号", "桑宁梦见吊桥断裂，醒来后桥况未知", "叶澄与陆禾约好清晨修补船篷", "纪川看见温棠手背有伤口，不知道原因");
        var certainty=List.of(Certainty.BELIEVED,Certainty.REPORTED,Certainty.OBSERVED,Certainty.BELIEVED,Certainty.OBSERVED,Certainty.OBSERVED);
        UUID knower=character.getId();
        if(number==3){var other=new CharacterCard();other.setStory(manuscript.getOutline().getStory());other.setName("顾蘅");em.persistAndFlush(other);knower=other.getId();}
        var policy=new NarrativeContextDtos.Policy(number==6?NarrativeContextDtos.Perspective.FIRST_PERSON:NarrativeContextDtos.Perspective.LIMITED_THIRD,true,Map.of(scene2,character.getId()));
        var entries=new ArrayList<NarrativeContextDtos.Entry>();
        entries.add(new NarrativeContextDtos.Entry(UUID.randomUUID(),NarrativeContextDtos.EntryKind.PLAN,item.path("plan").asText(),scene2,List.of(),false,List.of(),false));
        if(number==5)entries.add(new NarrativeContextDtos.Entry(UUID.randomUUID(),NarrativeContextDtos.EntryKind.BACKGROUND,"叶澄使用她，陆禾使用他；两人互知称谓。",scene2,List.of(character.getId()),false,List.of(),false));
        em.flush();configure(new NarrativeContextDtos.Document(policy,List.of(),entries));
        var a=approve(scene1);var op=operation(a);
        var evidence=List.of(new Evidence("b1",item.path("source").asText(),null,null));
        var assertion=new Assertion(character.getName(),character.getId(),item.path("source").asText(),kinds.get(number-1),kinds.get(number-1)==Kind.BELIEF?character.getId():null,null,"作者说明："+item.path("source").asText(),evidence,null,List.of(new Knowledge(knower,evidence,"作者核对："+item.path("source").asText(),new KnowledgeView(safe.get(number-1),kinds.get(number-1),certainty.get(number-1)))));
        store(a,op,assertion);submit(a,null);
        var names=Set.of("com.ainovel.app.narrative.NarrativeContextService","com.ainovel.app.manuscript.SceneGenerationPromptBuilder","com.ainovel.app.prompt.PromptAssemblyService","com.ainovel.app.manuscript.SceneGenerationContext");
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{root.resolve("baseline-classes").toUri().toURL()},getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {if(names.stream().noneMatch(n->name.equals(n)||name.startsWith(n+"$")))return super.loadClass(name,resolve);var c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;}
            }
        }) {
            var constructor=loader.loadClass("com.ainovel.app.narrative.NarrativeContextService").getConstructors()[0];
            Object baseline=constructor.newInstance(java.util.Arrays.stream(constructor.getParameterTypes()).map(beanFactory::getBean).toArray());
            org.springframework.test.util.ReflectionTestUtils.setField(baseline,"branches",beanFactory.getBean(com.ainovel.app.v2.repo.V2ManuscriptBranchRepository.class));
            org.springframework.test.util.ReflectionTestUtils.setField(baseline,"candidates",beanFactory.getBean(NarrativeGenerationCandidateRepository.class));
            var oldPreview=(NarrativeContextDtos.Preview)baseline.getClass().getMethod("preview",User.class,UUID.class,UUID.class,UUID.class,NarrativeContextDtos.View.class,UUID.class,int.class).invoke(baseline,user,manuscript.getId(),branch,scene2,NarrativeContextDtos.View.SCENE,null,3500);
            var newPreview=preview(NarrativeContextDtos.View.SCENE,null);
            var outputs=new LinkedHashMap<String,Object>();outputs.put("case",item);
            for(String version:List.of("baseline","candidate")) {
                var p=version.equals("baseline")?oldPreview:newPreview;
                var compilerVersion=version.equals("baseline")?"scene-isolation-h2-v1":NarrativeContextService.VERSION;
                var manifest=new com.ainovel.app.manuscript.context.SceneDraftContextManifest(compilerVersion,p.contextHash(),"SHA-256",p.tokenBudget(),p.tokenUsed(),manuscript.getOutline().getStory().getId(),manuscript.getOutline().getId(),manuscript.getId(),scene2,null,1,2,"",List.of(),List.of(),p.stamp());
                var compiled=new com.ainovel.app.manuscript.context.CompiledSceneDraftContext(p.content(),manifest,"",List.of(),List.of(),List.of(),"");
                Object builder=version.equals("baseline")?loader.loadClass("com.ainovel.app.manuscript.SceneGenerationPromptBuilder").getConstructor().newInstance():new com.ainovel.app.manuscript.SceneGenerationPromptBuilder();
                Object assembly=version.equals("baseline")?loader.loadClass("com.ainovel.app.prompt.PromptAssemblyService").getConstructor().newInstance():new com.ainovel.app.prompt.PromptAssemblyService();
                org.springframework.test.util.ReflectionTestUtils.setField(builder,"promptAssemblyService",assembly);
                org.springframework.test.util.ReflectionTestUtils.setField(builder,"slopPatternSamplingService",new com.ainovel.app.quality.SlopPatternSamplingService(new com.ainovel.app.quality.SlopPatternRegistry()));
                var sceneClass=(version.equals("baseline")?loader:getClass().getClassLoader()).loadClass("com.ainovel.app.manuscript.SceneGenerationContext");
                var sceneConstructor=sceneClass.getDeclaredConstructor(UUID.class,String.class,String.class,Integer.class,String.class,String.class,Integer.class,List.class,List.class);sceneConstructor.setAccessible(true);
                var scene=sceneConstructor.newInstance(scene2,"隐藏标题","隐藏摘要",1,"隐藏场景","隐藏规划",2,List.of(),List.of());
                for(var mode:com.ainovel.app.manuscript.GenerationMode.values()) {
                    var prompt=(com.ainovel.app.prompt.AssembledPrompt)builder.getClass().getMethod("build",User.class,Story.class,sceneClass,String.class,String.class,String.class,int.class,int.class,int.class,int.class,com.ainovel.app.manuscript.GenerationMode.class,com.ainovel.app.manuscript.context.CompiledSceneDraftContext.class)
                        .invoke(builder,user,manuscript.getOutline().getStory(),scene,"隐藏人物","隐藏前文",null,0,1,600,900,mode,compiled);
                    outputs.put(version+"-"+mode,Map.of("messages",prompt.messages(),"preview",p,"promptVersion",compilerVersion));
                }
            }
            java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(outputs));
        }
    }

    /** Offline request fixtures only: opt-in export never calls a model or writes the development database. */
    @ParameterizedTest
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="h23.exportBoundaryRequests",matches="true")
    @org.junit.jupiter.params.provider.ValueSource(ints={1,2,3,4})
    void exportFrozenBoundaryComparisonRequests(int number) throws Exception {
        var root=java.nio.file.Path.of("../artifacts/h23-quality-20260922/boundary-v4");
        var cases=json.readTree(root.resolve("frozen-cases.json").toFile());
        var item=cases.path("cases").get(number-1);String id=item.path("id").asText();
        var output=root.resolve("requests/"+id+".json");
        assertFalse(java.nio.file.Files.exists(output),"Frozen requests must not be silently overwritten");
        character.setName(item.path("pov").asText());
        character.setDetails("UNCLASSIFIED_SECRET_"+id);
        manuscript.setSectionsJson(write(Map.of(scene1,"<p>"+item.path("source").asText()+"</p>",scene2,"")));
        Kind kind=Kind.valueOf(item.path("kind").asText());
        Certainty certainty=Certainty.valueOf(item.path("certainty").asText());
        UUID knower=character.getId();
        var policy=new NarrativeContextDtos.Policy(item.path("firstPerson").asBoolean()?NarrativeContextDtos.Perspective.FIRST_PERSON:NarrativeContextDtos.Perspective.LIMITED_THIRD,true,Map.of(scene2,character.getId()));
        var entries=new ArrayList<NarrativeContextDtos.Entry>();
        entries.add(new NarrativeContextDtos.Entry(UUID.randomUUID(),NarrativeContextDtos.EntryKind.PLAN,item.path("plan").asText(),scene2,List.of(),false,List.of(),false));
        em.flush();configure(new NarrativeContextDtos.Document(policy,List.of(),entries));
        var a=approve(scene1);var op=operation(a);
        var evidence=List.of(new Evidence("b1",item.path("source").asText(),null,null));
        var assertion=new Assertion(character.getName(),character.getId(),item.path("source").asText(),kind,kind==Kind.BELIEF?character.getId():null,null,"作者说明："+item.path("source").asText(),evidence,null,List.of(new Knowledge(knower,evidence,"作者核对："+item.path("source").asText(),new KnowledgeView(item.path("safe").asText(),kind,certainty,item.path("eventActor").isNull()?null:item.path("eventActor").asText(),item.path("acquisitionBasis").isNull()?null:item.path("acquisitionBasis").asText()))));
        store(a,op,assertion);submit(a,null);
        var names=Set.of("com.ainovel.app.narrative.NarrativeContextService","com.ainovel.app.manuscript.SceneGenerationPromptBuilder","com.ainovel.app.prompt.PromptAssemblyService","com.ainovel.app.manuscript.SceneGenerationContext");
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{root.resolve("baseline-classes").toUri().toURL()},getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {if(names.stream().noneMatch(n->name.equals(n)||name.startsWith(n+"$")))return super.loadClass(name,resolve);var c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;}
            }
        }) {
            var constructor=loader.loadClass("com.ainovel.app.narrative.NarrativeContextService").getConstructors()[0];
            Object baseline=constructor.newInstance(java.util.Arrays.stream(constructor.getParameterTypes()).map(beanFactory::getBean).toArray());
            org.springframework.test.util.ReflectionTestUtils.setField(baseline,"branches",beanFactory.getBean(com.ainovel.app.v2.repo.V2ManuscriptBranchRepository.class));
            org.springframework.test.util.ReflectionTestUtils.setField(baseline,"candidates",beanFactory.getBean(NarrativeGenerationCandidateRepository.class));
            var oldPreview=(NarrativeContextDtos.Preview)baseline.getClass().getMethod("preview",User.class,UUID.class,UUID.class,UUID.class,NarrativeContextDtos.View.class,UUID.class,int.class).invoke(baseline,user,manuscript.getId(),branch,scene2,NarrativeContextDtos.View.SCENE,null,3500);
            var newPreview=preview(NarrativeContextDtos.View.SCENE,null);
            var outputs=new LinkedHashMap<String,Object>();outputs.put("case",item);
            for(String version:List.of("baseline","candidate")) {
                var p=version.equals("baseline")?oldPreview:newPreview;
                var compilerVersion=version.equals("baseline")?"scene-isolation-h2-v3":NarrativeContextService.VERSION;
                var manifest=new com.ainovel.app.manuscript.context.SceneDraftContextManifest(compilerVersion,p.contextHash(),"SHA-256",p.tokenBudget(),p.tokenUsed(),manuscript.getOutline().getStory().getId(),manuscript.getOutline().getId(),manuscript.getId(),scene2,null,1,2,"",List.of(),List.of(),p.stamp());
                var compiled=new com.ainovel.app.manuscript.context.CompiledSceneDraftContext(p.content(),manifest,"",List.of(),List.of(),List.of(),"");
                Object builder=version.equals("baseline")?loader.loadClass("com.ainovel.app.manuscript.SceneGenerationPromptBuilder").getConstructor().newInstance():new com.ainovel.app.manuscript.SceneGenerationPromptBuilder();
                Object assembly=version.equals("baseline")?loader.loadClass("com.ainovel.app.prompt.PromptAssemblyService").getConstructor().newInstance():new com.ainovel.app.prompt.PromptAssemblyService();
                org.springframework.test.util.ReflectionTestUtils.setField(builder,"promptAssemblyService",assembly);
                org.springframework.test.util.ReflectionTestUtils.setField(builder,"slopPatternSamplingService",new com.ainovel.app.quality.SlopPatternSamplingService(new com.ainovel.app.quality.SlopPatternRegistry()));
                var sceneClass=(version.equals("baseline")?loader:getClass().getClassLoader()).loadClass("com.ainovel.app.manuscript.SceneGenerationContext");
                var sceneConstructor=sceneClass.getDeclaredConstructor(UUID.class,String.class,String.class,Integer.class,String.class,String.class,Integer.class,List.class,List.class);sceneConstructor.setAccessible(true);
                var scene=sceneConstructor.newInstance(scene2,"隐藏标题","隐藏摘要",1,"隐藏场景","隐藏规划",2,List.of(),List.of());
                for(var mode:com.ainovel.app.manuscript.GenerationMode.values()) {
                    var prompt=(com.ainovel.app.prompt.AssembledPrompt)builder.getClass().getMethod("build",User.class,Story.class,sceneClass,String.class,String.class,String.class,int.class,int.class,int.class,int.class,com.ainovel.app.manuscript.GenerationMode.class,com.ainovel.app.manuscript.context.CompiledSceneDraftContext.class)
                        .invoke(builder,user,manuscript.getOutline().getStory(),scene,"隐藏人物","隐藏前文",null,0,1,600,900,mode,compiled);
                    outputs.put(version+"-"+mode,Map.of("messages",prompt.messages(),"preview",p,"promptVersion",compilerVersion));
                }
            }
            java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(outputs));
        }
    }

    @Test void h2MachineKnowledgeNeedsAuthorAcceptanceAndDoesNotMakeAnUtteranceTrue() {
        assertFalse(context.enabled(manuscript));
        configure(document(List.of(),List.of()));
        var a=approve(scene1); var op=operation(a);
        assertTrue(service.input(user,a.extractionId(),op).knowledgeEnabled());
        Assertion candidate=new Assertion("林青",character.getId(),"林青声称桥断",Kind.UTTERANCE,null,null,"未核实",
                List.of(new Evidence("b1","桥断了",null,null)),null,List.of(new Knowledge(character.getId(),List.of(new Evidence("b1","林青说：桥断了",null,null)),"说出不等于真实",new KnowledgeView("林青声称桥断，未核实",Kind.UTTERANCE,Certainty.REPORTED))));
        store(a,op,candidate);
        assertFalse(preview(NarrativeContextDtos.View.CHARACTER,character.getId()).content().contains("林青声称桥断"));
        submit(a,null);
        String content=preview(NarrativeContextDtos.View.CHARACTER,character.getId()).content();
        assertTrue(content.contains("UTTERANCE")); assertTrue(content.contains("未核实"));
        assertFalse(content.contains("秘密结局")); assertFalse(content.contains("未来秘密"));
        var outsider=new CharacterCard(); outsider.setStory(manuscript.getOutline().getStory()); outsider.setName("局外人"); em.persistAndFlush(outsider);
        assertFalse(preview(NarrativeContextDtos.View.CHARACTER,outsider.getId()).content().contains("林青声称桥断"));
        assertFalse(context.preview(user,manuscript.getId(),branch,scene1,NarrativeContextDtos.View.CHARACTER,character.getId(),3500).content().contains("林青声称桥断"));
    }

    @Test void h2CharacterProjectionNeverIncludesAuthorCorrectionQuoteOrMetadata() {
        configure(document(List.of(),List.of()));
        var a=approve(scene1);var op=operation(a);
        var candidate=new Assertion("AUTHOR_SUBJECT_SECRET",character.getId(),"林青误信同伴背叛，实际同伴忠诚 AUTHOR_TRUTH",Kind.BELIEF,character.getId(),"AUTHOR_TIME_SECRET","AUTHOR_CORRECTION_SECRET",
                List.of(new Evidence("b2","林青误以为同伴背叛，昨夜梦见大火",null,null)),null,
                List.of(new Knowledge(character.getId(),List.of(new Evidence("b2","林青误以为同伴背叛，昨夜梦见大火",null,null)),"AUTHOR_KNOWLEDGE_NOTE",new KnowledgeView("林青相信同伴已经背叛自己",Kind.BELIEF,Certainty.BELIEVED))));
        store(a,op,candidate);submit(a,null);
        for(var view:List.of(NarrativeContextDtos.View.CHARACTER,NarrativeContextDtos.View.SCENE)) {
            String sent=preview(view,character.getId()).content();
            assertTrue(sent.contains("林青相信同伴已经背叛自己"));
            assertTrue(sent.contains("BELIEF"));assertTrue(sent.contains("BELIEVED"));
            assertTrue(sent.contains("不得为这些内容补写亲眼目睹"));
            assertTrue(sent.contains("不补造关键往事"));
            assertFalse(sent.contains("AUTHOR_"));assertFalse(sent.contains("昨夜梦见大火"));assertFalse(sent.contains("实际同伴忠诚"));
        }
        assertTrue(state().records().get(0).assertion().statement().contains("AUTHOR_TRUTH"));
    }

    @Test void h2ApprovedOriginsRoundTripWithoutTurningBeliefIntoFact() throws Exception {
        configure(document(List.of(),List.of()));
        var a=approve(scene1);var op=operation(a);
        var evidence=List.of(new Evidence("b2","林青误以为同伴背叛",null,null));
        var safe=new KnowledgeView("林青相信同伴背叛",Kind.BELIEF,Certainty.BELIEVED,"信念中的同伴", "林青自己的猜测，没有目击或核实");
        store(a,op,new Assertion("林青",character.getId(),"作者纠错 AUTHOR_TRUTH",Kind.BELIEF,character.getId(),null,"AUTHOR_NOTE",evidence,null,
                List.of(new Knowledge(character.getId(),evidence,"AUTHOR_SOURCE",safe))));
        assertFalse(preview(NarrativeContextDtos.View.SCENE,null).content().contains("信念中的同伴"));
        submit(a,null);
        assertEquals(safe,state().records().get(0).assertion().knowledge().get(0).view());
        String sent=preview(NarrativeContextDtos.View.SCENE,null).content();
        assertTrue(sent.contains("信念中的同伴"));assertTrue(sent.contains("没有目击或核实"));
        assertTrue(sent.contains("BELIEF"));assertTrue(sent.contains("BELIEVED"));assertFalse(sent.contains("AUTHOR_"));
        assertEquals(safe,json.readValue(json.writeValueAsString(safe),KnowledgeView.class));
        var legacy=json.readValue("{\"content\":\"林青相信同伴背叛\",\"kind\":\"BELIEF\",\"certainty\":\"BELIEVED\"}",KnowledgeView.class);
        assertNull(legacy.eventActor());assertNull(legacy.acquisitionBasis());
        assertNull(new KnowledgeView("表述",Kind.FACT,Certainty.UNKNOWN,"  ","\t").eventActor());
    }

    @Test void h2RequiredBoundaryCannotSilentlyExceedCharacterOrReaderBudget() {
        configure(document(List.of(),List.of()));
        for(var view:List.of(NarrativeContextDtos.View.CHARACTER,NarrativeContextDtos.View.READER)) {
            var failure=assertThrows(ApiStatusException.class,()->context.preview(user,manuscript.getId(),branch,scene2,view,character.getId(),256));
            assertEquals("H2_REQUIRED_CONTEXT_TOO_LARGE",failure.getMessage());
        }
    }

    @Test void h2LegacyKnowledgeIsExcludedUntilAuthorAddsSeparateSafeWording() {
        configure(document(List.of(),List.of()));
        var a=approve(scene1);var op=operation(a);
        store(a,op,new Assertion("林青",character.getId(),"旧记录作者事实",Kind.BELIEF,character.getId(),null,"隐藏纠错",
                List.of(new Evidence("b2","林青误以为同伴背叛",null,null)),null,List.of(new Knowledge(character.getId(),List.of(new Evidence("b2","林青误以为同伴背叛",null,null)),"旧说明"))));
        submit(a,null);
        var before=preview(NarrativeContextDtos.View.CHARACTER,character.getId());
        assertFalse(before.content().contains("旧记录作者事实"));
        assertTrue(before.excluded().stream().anyMatch(f->f.reason().contains("没有已确认的人物可用表述")));
        var grant=new NarrativeContextDtos.Grant(UUID.randomUUID(),state().records().get(0).id(),character.getId(),a.approvalId(),List.of(new Evidence("b2","林青误以为同伴背叛",null,null)),scene2,"作者纠错仍不发送",false,new KnowledgeView("林青相信同伴背叛",Kind.BELIEF,Certainty.BELIEVED));
        configure(document(List.of(grant),List.of()));
        String sent=preview(NarrativeContextDtos.View.CHARACTER,character.getId()).content();
        assertTrue(sent.contains("林青相信同伴背叛"));assertFalse(sent.contains("作者纠错"));assertFalse(sent.contains("旧记录作者事实"));
        assertTrue(sent.contains("\"eventActor\":\"NOT_PROVIDED\""));
        assertTrue(sent.contains("\"acquisitionBasis\":\"NOT_PROVIDED\""));
        var supplemented=new NarrativeContextDtos.Grant(grant.id(),grant.recordId(),grant.characterId(),grant.approvalId(),grant.evidence(),grant.fromSceneId(),grant.uncertainty(),false,
                new KnowledgeView("林青相信同伴背叛",Kind.BELIEF,Certainty.BELIEVED,"信念中的同伴","仅有自己的猜测"));
        var updated=configure(document(List.of(supplemented),List.of()));
        assertEquals("仅有自己的猜测",updated.document().grants().get(0).view().acquisitionBasis());
        assertTrue(preview(NarrativeContextDtos.View.SCENE,null).content().contains("仅有自己的猜测"));
    }

    @Test void h2SupplementaryEvidenceIsScopedAndInvalidationDoesNotResurrect() {
        var a=complete(scene1,assertion(Kind.UTTERANCE,"林青声称桥断","桥断了","b1",null));
        UUID record=state().records().get(0).id();
        var grant=new NarrativeContextDtos.Grant(UUID.randomUUID(),record,character.getId(),a.approvalId(),List.of(new Evidence("b1","林青说：桥断了",null,null)),scene2,"发言",false,new KnowledgeView("林青声称桥断",Kind.UTTERANCE,Certainty.REPORTED));
        configure(document(List.of(grant),List.of()));
        assertTrue(preview(NarrativeContextDtos.View.SCENE,null).content().contains("林青声称桥断"));
        manuscript.setSectionsJson(write(Map.of(scene1,"<p>林青沉默。</p>",scene2,"<p>等消息。</p>"))); em.flush();
        context.reconcileManuscript(manuscript.getId());
        assertTrue(context.state(user,manuscript.getId(),branch).document().grants().get(0).stale());
        manuscript.setSectionsJson(write(Map.of(scene1,TEXT,scene2,"<p>等消息。</p>"))); em.flush();
        assertFalse(preview(NarrativeContextDtos.View.CHARACTER,character.getId()).content().contains("林青声称桥断"));
        assertTrue(context.history(user,manuscript.getId(),branch).size()>=2);
    }

    @Test void h2PlansNeverEnterReaderOrCharacterViewsAndUnclassifiedNotesAreExcluded() {
        var plan=new NarrativeContextDtos.Entry(UUID.randomUUID(),NarrativeContextDtos.EntryKind.PLAN,"本场准备打开蓝盒，尚未发生",scene2,List.of(),true,List.of(),false);
        configure(document(List.of(),List.of(plan)));
        assertTrue(preview(NarrativeContextDtos.View.SCENE,null).content().contains("本场准备打开蓝盒"));
        assertFalse(preview(NarrativeContextDtos.View.CHARACTER,character.getId()).content().contains("打开蓝盒"));
        String reader=preview(NarrativeContextDtos.View.READER,null).content();
        assertTrue(reader.contains("林青误以为同伴背叛"));
        assertFalse(reader.contains("打开蓝盒")); assertFalse(reader.contains("仍在等消息"));
        assertFalse(reader.contains("秘密"));
    }

    @Test void h2ConfigReplaysAreAtomicAndGenerationStampRejectsChanges() {
        var s=context.state(user,manuscript.getId(),branch);
        var req=new NarrativeContextDtos.Update(s.manuscriptVersion(),s.canonRevision(),s.revision(),s.settingsRevision(),true,document(List.of(),List.of()));
        var result=context.update(user,manuscript.getId(),branch,req,"h2-key");
        assertEquals(result,context.update(user,manuscript.getId(),branch,req,"h2-key"));
        var stamp=preview(NarrativeContextDtos.View.SCENE,null).stamp();
        configure(document(List.of(),List.of()));
        assertThrows(ApiStatusException.class,()->context.requireStamp(user,stamp));
        assertThrows(ApiStatusException.class,()->context.update(user,manuscript.getId(),branch,req,"stale-key"));
        User other=new User(); other.setId(UUID.randomUUID());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->context.state(other,manuscript.getId(),branch));
    }

    @Test void h2NewBranchStartsWithoutKnowledgeOrContract() {
        configure(document(List.of(),List.of()));
        UUID base=(UUID)versions.listVersions(manuscript,user).get(0).get("id");
        UUID fork=(UUID)versions.createBranch(manuscript,user,Map.of("name","H2 fork","sourceVersionId",base)).get("id");
        versions.checkoutBranch(manuscript,user,fork); em.flush();
        assertTrue(context.state(user,manuscript.getId(),fork).enabled());
        assertNull(context.state(user,manuscript.getId(),fork).document());
        assertThrows(ApiStatusException.class,()->context.preview(user,manuscript.getId(),fork,scene2,NarrativeContextDtos.View.SCENE,null,3500));
    }

    @BeforeEach void setup() {
        user = new User(); user.setUsername("narrative-" + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@example.com"); user.setPasswordHash("test"); em.persist(user);
        Story story = new Story(); story.setUser(user); story.setTitle("证据测试"); story.setSynopsis("秘密结局不得入输入"); em.persist(story);
        Outline outline = new Outline(); outline.setStory(story); outline.setTitle("大纲");
        outline.setContentJson(topology(List.of(scene1, scene2))); em.persist(outline);
        character = new CharacterCard(); character.setStory(story); character.setName("林青");
        character.setDetails("未来秘密不得入输入"); em.persist(character);
        manuscript = new Manuscript(); manuscript.setOutline(outline); manuscript.setTitle("正文");
        manuscript.setSectionsJson(write(Map.of(scene1, TEXT, scene2, "<p>林青仍在等消息。</p>"))); em.persist(manuscript);
        versions.listVersions(manuscript, user); em.flush(); branch = manuscript.getCurrentBranchId();
    }
    @Test void authorGateEvidenceAndIdempotentReceiptsSurviveReload() {
        ApprovalRequest request = new ApprovalRequest(scene1, manuscript.getVersion(), 0L);
        Approved approved = service.approve(user, manuscript.getId(), branch, request, "approval");
        assertEquals(approved, service.approve(user, manuscript.getId(), branch, request, "approval"));
        UUID operation = operation(approved);
        assertFalse(write(service.input(user, approved.extractionId(), operation)).contains("秘密"));
        store(approved, operation, assertion(Kind.FACT, "同伴背叛", "林青误以为同伴背叛", "b2", null));
        assertTrue(state().records().isEmpty());
        Assertion edited = assertion(Kind.BELIEF, "林青误以为同伴背叛", "林青误以为同伴背叛", "b2", null);
        ReviewRequest review = reviewRequest(edited);
        ReviewResult receipt = service.review(user, manuscript.getId(), branch, approved.extractionId(), review, "review");
        assertEquals(receipt, service.review(user, manuscript.getId(), branch, approved.extractionId(), review, "review"));
        em.flush(); em.clear();
        var record = state().records().get(0);
        assertEquals(Kind.BELIEF, record.assertion().kind());
        assertEquals(character.getId(), record.assertion().holderCharacterId());
        var source = service.evidence(user, manuscript.getId(), branch, approved.approvalId());
        var evidence = record.assertion().evidence().get(0);
        String block = source.blocks().get(1).text();
        assertEquals(evidence.quote(), new String(block.codePoints().skip(evidence.start()).limit(evidence.end() - evidence.start()).toArray(), 0, evidence.end() - evidence.start()));
        assertTrue(em.find(V2ManuscriptVersion.class, source.versionId()).isNarrativeProtected());
        assertEquals(1, state().records().size());
    }
    @ParameterizedTest(name="author correction: {0}")
    @MethodSource("semanticFixtures")
    void evidenceValidationNeverSubstitutesForAuthorSemanticReview(String id, com.fasterxml.jackson.databind.JsonNode fixture) {
        manuscript.setSectionsJson(write(Map.of(scene1, "<p>" + fixture.path("text").asText() + "</p>"))); em.flush();
        var approved = approve(scene1);
        store(approved, operation(approved), assertion(Kind.FACT, fixture.path("modelStatement").asText(), fixture.path("quote").asText(), "b1", null));
        assertTrue(state().records().isEmpty());
        var kind = Kind.valueOf(fixture.path("kind").asText());
        var edited = new Assertion("林青", character.getId(), fixture.path("statement").asText(), kind,
                kind == Kind.BELIEF ? character.getId() : null, null, fixture.path("uncertainty").asText(),
                List.of(new Evidence("b1", fixture.path("quote").asText(), null, null)), null);
        submit(approved, edited);
        assertEquals(kind, state().records().get(0).assertion().kind());
        assertEquals(edited.statement(), state().records().get(0).assertion().statement());
        assertNull(state().records().get(0).assertion().worldTime());
    }
    static java.util.stream.Stream<Arguments> semanticFixtures() throws Exception {
        try (var source = NarrativeServiceTest.class.getResourceAsStream("/narrative/h1-semantic-fixtures.json")) {
            var fixtures = new ObjectMapper().readTree(source);
            return java.util.stream.StreamSupport.stream(fixtures.spliterator(), false)
                    .map(fixture -> Arguments.of(fixture.path("id").asText(), fixture)).toList().stream();
        }
    }
    @Test void sourceRevisionInvalidatesDependentRecordsAndPreservesHistory() {
        Approved first = complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        var second = approve(scene2); UUID operation = operation(second);
        assertEquals(1, service.input(user, second.extractionId(), operation).previousRecords().size());
        store(second, operation, assertion(Kind.FACT, "林青等待消息", "林青仍在等消息", "b1", null));
        submit(second, null);
        long previous = state().canonRevision();
        manuscript.setSectionsJson(write(Map.of(scene1, "<p>桥仍完好。</p>", scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        service.reconcileManuscript(manuscript.getId());
        assertEquals(List.of("STALE", "STALE"), state().records().stream().map(RecordView::status).toList());
        assertEquals(List.of("CONFIRMED", "CONFIRMED"), service.state(user, manuscript.getId(), branch, null, null, null, null, previous).records().stream().map(RecordView::status).toList());
        assertEquals(TEXT.replace("<p>", "").split("</p>")[0], service.evidence(user, manuscript.getId(), branch, first.approvalId()).blocks().get(0).text());
        manuscript.setSectionsJson(write(Map.of(scene1, TEXT, scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        assertEquals("STALE", state().records().get(0).status());
    }
    @Test void formatChangesKeepEvidenceButReorderAndDeleteInvalidate() {
        complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        manuscript.setSectionsJson(write(Map.of(scene1, TEXT.replace("桥断了", "<strong>桥断了</strong>"), scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        assertEquals("CONFIRMED", state().records().get(0).status());
        manuscript.getOutline().setContentJson(topology(List.of(scene2, scene1))); em.flush();
        assertEquals("STALE", state().records().get(0).status());
        manuscript.getOutline().setContentJson(topology(List.of(scene2))); em.flush();
        assertTrue(state().extractions().get(0).stale());
    }
    @Test void cancelledMalformedAndEmptyBatchesCannotCreatePartialState() {
        var first = approve(scene1); UUID operation = operation(first);
        service.storeResult(user, first.extractionId(), operation, "broken JSON", Map.of("cost", 1), "test");
        assertEquals("INVALID_OUTPUT", state().extractions().get(0).status());
        service.storeResult(user, first.extractionId(), operation, "{\"candidates\":[]}", Map.of("cost", 2), "test");
        assertTrue(state().records().isEmpty());
        var empty = approve(scene1); UUID emptyOperation = operation(empty);
        service.storeResult(user, empty.extractionId(), emptyOperation, "{\"candidates\":[]}", Map.of(), "test");
        assertTrue(service.extraction(user, manuscript.getId(), branch, empty.extractionId()).candidates().isEmpty());
        var cancelled = approve(scene1); UUID cancelledOperation = operation(cancelled);
        store(cancelled, cancelledOperation, assertion(Kind.FACT, "不应入账", "桥断了", "b1", null));
        em.find(AiOperationRun.class, cancelledOperation).setStatus(AiOperationStatus.CANCELLED); em.flush();
        assertEquals("CANCELLED", service.extraction(user, manuscript.getId(), branch, cancelled.extractionId()).status());
        assertThrows(ApiStatusException.class, () -> submit(cancelled, null));
        assertTrue(state().records().isEmpty());
    }
    @Test void rejectsWrongEvidenceAndVersionsWithoutAcceptingAnyRecord() {
        var approved = approve(scene1); UUID operation = operation(approved);
        store(approved, operation, assertion(Kind.FACT, "拿到钥匙", "已经拿到钥匙", "b1", UUID.randomUUID()));
        assertNotNull(state().extractions().get(0).candidates().get(0).validationError());
        assertNull(state().extractions().get(0).candidates().get(0).assertion().supersedesId());
        assertThrows(ApiStatusException.class, () -> submit(approved, null));
        assertTrue(state().records().isEmpty());
        assertThrows(ApiStatusException.class, () -> service.approve(user, manuscript.getId(), branch, new ApprovalRequest(scene1, -1L, 0L), "bad-version"));
        manuscript.setSectionsJson("{}"); em.flush();
        assertTrue(service.extraction(user, manuscript.getId(), branch, approved.extractionId()).stale());
    }
    @Test void branchAndManuscriptAndOwnerIsolation() {
        var approved = complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        User other = new User(); other.setId(UUID.randomUUID());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.state(other, manuscript.getId(), branch, null, null, null, null, null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.evidence(other, manuscript.getId(), branch, approved.approvalId()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.review(other, manuscript.getId(), branch, approved.extractionId(), reviewRequest(null), "foreign-review"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.approve(other, manuscript.getId(), branch, new ApprovalRequest(scene1, manuscript.getVersion(), state().canonRevision()), "foreign-approval"));
        Manuscript second = new Manuscript(); second.setTitle("另一稿件"); second.setOutline(manuscript.getOutline()); second.setSectionsJson(manuscript.getSectionsJson()); em.persistAndFlush(second);
        versions.listVersions(second, user); em.flush();
        assertTrue(service.state(user, second.getId(), second.getCurrentBranchId(), null, null, null, null, null).records().isEmpty());
        assertThrows(ApiStatusException.class, () -> service.evidence(user, second.getId(), second.getCurrentBranchId(), approved.approvalId()));
        assertThrows(ApiStatusException.class, () -> service.state(user, second.getId(), branch, null, null, null, null, null));
        assertThrows(ApiStatusException.class, () -> service.review(user, second.getId(), second.getCurrentBranchId(), approved.extractionId(), reviewRequest(null), "foreign-manuscript"));
        V2ManuscriptBranch fork = new V2ManuscriptBranch(); fork.setManuscript(manuscript); fork.setName("新分支"); fork.setStatus("active"); em.persistAndFlush(fork);
        assertTrue(service.state(user, manuscript.getId(), fork.getId(), null, null, null, null, null).records().isEmpty());
    }
    @Test void h2ChapterTenCannotSeeLaterTextAndUnknownTimeNeverGrantsKnowledge() {
        var scenes=new ArrayList<UUID>(); for(int i=0;i<12;i++)scenes.add(UUID.randomUUID());
        var chapters=new ArrayList<Map<String,Object>>();var sections=new LinkedHashMap<String,String>();
        for(int i=0;i<12;i++) {
            chapters.add(Map.of("id",UUID.randomUUID(),"title","章"+i,"order",i+1,"scenes",List.of(Map.of("id",scenes.get(i),"title","场","order",1))));
            sections.put(scenes.get(i).toString(),"<p>"+(i<9?"可见前文":"不得进入第十章输入")+i+"</p>");
        }
        manuscript.getOutline().setContentJson(write(Map.of("chapters",chapters)));manuscript.setSectionsJson(write(sections));em.flush();
        configure(new NarrativeContextDtos.Document(new NarrativeContextDtos.Policy(NarrativeContextDtos.Perspective.OMNISCIENT,false,Map.of()),List.of(),List.of()));
        var p=context.preview(user,manuscript.getId(),branch,scenes.get(9),NarrativeContextDtos.View.READER,null,3500);
        assertTrue(p.content().contains("可见前文8"));assertFalse(p.content().contains("不得进入"));
    }

    @Test void h2BeliefWithoutExplicitKnowledgeIsNotGivenToItsHolderAndReorderingInvalidatesPlans() {
        complete(scene1,assertion(Kind.BELIEF,"林青误信同伴背叛","林青误以为同伴背叛","b2",null));
        var plan=new NarrativeContextDtos.Entry(UUID.randomUUID(),NarrativeContextDtos.EntryKind.PLAN,"林青准备出门",scene2,List.of(),false,List.of(),false);
        configure(document(List.of(),List.of(plan)));
        assertFalse(preview(NarrativeContextDtos.View.CHARACTER,character.getId()).content().contains("林青误信同伴背叛"));
        manuscript.getOutline().setContentJson(topology(List.of(scene2,scene1)));em.flush();context.reconcileManuscript(manuscript.getId());
        assertTrue(context.state(user,manuscript.getId(),branch).document().entries().get(0).stale());
        manuscript.getOutline().setContentJson(topology(List.of(scene1,scene2)));em.flush();
        assertTrue(context.state(user,manuscript.getId(),branch).document().entries().get(0).stale());
    }
    @Test void incompleteOrInvalidLaterDecisionNeverPartiallyCommits() {
        var approved = approve(scene1); UUID operation = operation(approved);
        var valid = assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null);
        service.storeResult(user, approved.extractionId(), operation,
                write(Map.of("candidates", List.of(valid, valid))), Map.of(), "test");
        assertThrows(ApiStatusException.class, () -> submit(approved, null));
        var invalid = assertion(Kind.FACT, "错误引文", "原文中没有这句话", "b1", null);
        var decisions = List.of(new ReviewItem("c1", Decision.ACCEPT, null), new ReviewItem("c2", Decision.ACCEPT, invalid));
        assertThrows(ApiStatusException.class, () -> service.review(user, manuscript.getId(), branch, approved.extractionId(),
                new ReviewRequest(manuscript.getVersion(), 0L, decisions, List.of()), "invalid-later"));
        assertEquals(0, state().canonRevision());
        assertTrue(state().records().isEmpty());
        var complete = List.of(new ReviewItem("c1", Decision.ACCEPT, null), new ReviewItem("c2", Decision.REJECT, null));
        assertThrows(ApiStatusException.class, () -> service.review(user, manuscript.getId(), branch, approved.extractionId(),
                new ReviewRequest(manuscript.getVersion(), 1L, complete, List.of()), "conflict"));
        assertTrue(state().records().isEmpty());
        service.review(user, manuscript.getId(), branch, approved.extractionId(),
                new ReviewRequest(manuscript.getVersion(), 0L, complete, List.of()), "complete");
        assertEquals(1, state().records().size());
        assertEquals(1, state().canonRevision());
    }
    @Test void explicitReplacementDoesNotInvalidateItselfAndAbsenceNeverDeletes() {
        complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        UUID original = state().records().get(0).id();
        complete(scene2, assertion(Kind.FACT, "林青仍等待", "林青仍在等消息", "b1", original));
        assertEquals(List.of("SUPERSEDED", "CONFIRMED"), state().records().stream().map(RecordView::status).toList());
        var empty = approve(scene2); var operation = operation(empty);
        service.storeResult(user, empty.extractionId(), operation, "{\"candidates\":[]}", Map.of(), "test");
        service.review(user, manuscript.getId(), branch, empty.extractionId(), new ReviewRequest(manuscript.getVersion(), state().canonRevision(), List.of(), List.of()), "empty");
        assertEquals("CONFIRMED", state().records().get(1).status());
    }
    @Test void protectedAutoSnapshotSurvivesRetentionLimit() {
        var approved = complete(scene1, assertion(Kind.UTTERANCE, "声称桥断", "桥断了", "b1", null));
        UUID protectedId = service.evidence(user, manuscript.getId(), branch, approved.approvalId()).versionId();
        assertEquals("auto", em.find(V2ManuscriptVersion.class, protectedId).getSnapshotType());
        versions.updateAutoSave(user, Map.of("maxAutoVersions", 10));
        for (int i = 0; i < 10; i++) {
            manuscript.setSectionsJson(write(Map.of(scene1, TEXT, scene2, "<p>第" + i + "天等消息。</p>")));
            versions.createVersion(manuscript, user, Map.of("snapshotType", "auto"));
        }
        em.flush(); em.clear();
        assertNotNull(em.find(V2ManuscriptVersion.class, protectedId));
        assertEquals(11, versions.listVersions(manuscript.getId()).size());
        assertEquals("CONFIRMED", state().records().get(0).status());
    }
    @Test void checkoutMergeAndRollbackKeepSeparateLedgersAndInvalidateTarget() {
        var mainApproval = complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        UUID main = branch, baseline = service.evidence(user, manuscript.getId(), branch, mainApproval.approvalId()).versionId();
        UUID fork = (UUID) versions.createBranch(manuscript, user, Map.of("name", "替代剧情", "sourceVersionId", baseline)).get("id");
        versions.checkoutBranch(manuscript, user, fork); em.flush(); branch = fork;
        assertTrue(state().records().isEmpty());
        manuscript.setSectionsJson(write(Map.of(scene1, "<p>林青发现桥完好。</p>", scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        complete(scene1, assertion(Kind.FACT, "桥完好", "桥完好", "b1", null));
        assertEquals("CONFIRMED", service.state(user, manuscript.getId(), main, null, null, null, null, null).records().get(0).status());
        versions.mergeBranch(manuscript, user, fork, Map.of()); em.flush(); branch = main;
        assertEquals(1, state().records().size());
        assertEquals("STALE", state().records().get(0).status());
        assertEquals("CONFIRMED", service.state(user, manuscript.getId(), fork, null, null, null, null, null).records().get(0).status());
        versions.rollback(manuscript, user, baseline); em.flush();
        assertEquals("STALE", state().records().get(0).status());
        assertEquals(TEXT, json.valueToTree(readSections()).path(scene1.toString()).asText());
    }
    Map<?, ?> readSections() { try { return json.readValue(manuscript.getSectionsJson(), Map.class); } catch (Exception e) { throw new RuntimeException(e); } }
    String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new RuntimeException(e); } }
    String topology(List<UUID> scenes) { return write(Map.of("chapters", List.of(Map.of("id", chapter, "title", "一", "scenes", scenes.stream().map(id -> Map.of("id", id, "title", "场景")).toList())))); }
    StateView state() { return service.state(user, manuscript.getId(), branch, null, null, null, null, null); }
    Approved approve(UUID scene) { return service.approve(user, manuscript.getId(), branch, new ApprovalRequest(scene, manuscript.getVersion(), state().canonRevision()), UUID.randomUUID().toString()); }
    UUID operation(Approved approved) { AiOperationRun run = new AiOperationRun(); run.setUser(user); run.setOperationType(NarrativeExtractionHandler.TYPE); run.setStatus(AiOperationStatus.RUNNING); em.persistAndFlush(run); service.attachOperation(approved.extractionId(), run.getId()); return run.getId(); }
    Assertion assertion(Kind kind, String statement, String quote, String block, UUID replaces) { return new Assertion("林青", character.getId(), statement, kind, kind == Kind.BELIEF ? character.getId() : null, null, "由作者核对", List.of(new Evidence(block, quote, 999, 1000)), replaces); }
    void store(Approved approved, UUID operation, Assertion assertion) { service.storeResult(user, approved.extractionId(), operation, write(Map.of("candidates", List.of(assertion))), Map.of("cost", 1), "test"); }
    ReviewRequest reviewRequest(Assertion edited) { return new ReviewRequest(manuscript.getVersion(), state().canonRevision(), List.of(new ReviewItem("c1", Decision.ACCEPT, edited)), List.of()); }
    void submit(Approved approved, Assertion edited) { service.review(user, manuscript.getId(), branch, approved.extractionId(), reviewRequest(edited), UUID.randomUUID().toString()); }
    Approved complete(UUID scene, Assertion assertion) { var approved = approve(scene); store(approved, operation(approved), assertion); submit(approved, assertion); return approved; }
}
