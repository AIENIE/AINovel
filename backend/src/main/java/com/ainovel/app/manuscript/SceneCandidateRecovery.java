package com.ainovel.app.manuscript;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** One approved local incident. Reads completed reservations only; never invokes AI or billing. */
public final class SceneCandidateRecovery {
    private static final UUID OP=UUID.fromString("a9917c9d-919b-44b1-986b-5afd7cd68cb3");
    private static final UUID MANUSCRIPT=UUID.fromString("9593673b-bb55-4276-a86b-e5d1bba6abff");
    private static final UUID SCENE=UUID.fromString("77334506-ebd2-4992-8375-1b2227ea9cf0");
    private SceneCandidateRecovery() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=1||!"--apply-local-incident".equals(args[0])) throw new IllegalArgumentException("Explicit local incident action required");
        String url=Objects.requireNonNull(System.getenv("DB_URL"));
        if(!url.startsWith("jdbc:mysql://localmysql.testhut.top:23306/ainovel?")) throw new IllegalArgumentException("Only the approved local database is supported");
        var json=new ObjectMapper().findAndRegisterModules();
        try(var c=DriverManager.getConnection(url,System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                try(var q=c.prepareStatement("SELECT payload_json,status FROM ai_operation_runs WHERE id=? FOR UPDATE")) {
                    q.setBytes(1,bytes(OP));try(var r=q.executeQuery()) {
                        if(!r.next()||!Set.of("FAILED","RECOVERY_REQUIRED").contains(r.getString("status"))) throw new IllegalStateException("Historical task is not failed/recoverable");
                        var payload=json.readTree(r.getString("payload_json"));
                        if(!MANUSCRIPT.toString().equals(payload.path("primaryId").asText())||!SCENE.toString().equals(payload.path("secondaryId").asText())) throw new IllegalStateException("Incident identity mismatch");
                    }
                }
                byte[] branch;
                try(var q=c.prepareStatement("SELECT current_branch_id FROM manuscripts WHERE id=?")) {
                    q.setBytes(1,bytes(MANUSCRIPT));try(var r=q.executeQuery()){if(!r.next()||(branch=r.getBytes(1))==null)throw new IllegalStateException("Current branch unavailable");}
                }
                List<Map<String,Object>> receipts=new ArrayList<>();
                try(var q=c.prepareStatement("SELECT id,result_content,prompt_tokens,completion_tokens,settled_amount,attempt_count,created_at FROM ai_credit_reservations WHERE idempotency_key LIKE ? AND status='COMPLETED' ORDER BY created_at,id")) {
                    q.setString(1,OP+":%");try(var r=q.executeQuery()) {
                        while(r.next()) {
                            var record=new LinkedHashMap<String,Object>();record.put("id",r.getBytes("id"));record.put("text",r.getString("result_content"));
                            for(String field:List.of("prompt_tokens","completion_tokens","settled_amount","attempt_count")) record.put(field,r.getObject(field));
                            record.put("created_at",r.getTimestamp("created_at"));receipts.add(record);
                        }
                    }
                }
                if(receipts.size()!=3) throw new IllegalStateException("Expected exactly three completed historical calls");
                int inserted=0;var counts=new ArrayList<Integer>();
                for(int i=0;i<receipts.size();i++) {
                    var receipt=receipts.get(i);String raw=(String)receipt.get("text");
                    if(raw==null||raw.isBlank())throw new IllegalStateException("Completed content is missing");
                    String text=SceneGenerationService.normalizeGeneratedText(raw,json);
                    int han=SceneGenerationService.countHanCharacters(text);counts.add(han);
                    var trace=new LinkedHashMap<String,Object>(receipt);trace.remove("text");trace.remove("created_at");trace.remove("id");
                    trace.put("reservationId",uuid((byte[])receipt.get("id")));trace.put("sourceIncomplete",true);
                    trace.put("contentSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));
                    try(var q=c.prepareStatement("INSERT INTO narrative_generation_candidates(id,manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at,operation_id,generation_sequence,min_han,max_han,actual_han,quality_status,source_kind) SELECT ?,?,?,?, ?,?,'LENGTH_REJECTED',?, ?,?,2800,3200,?,'UNKNOWN','RECOVERED' WHERE NOT EXISTS (SELECT 1 FROM narrative_generation_candidates WHERE operation_id=? AND generation_sequence=?)")) {
                        int p=1;q.setBytes(p++,bytes(UUID.nameUUIDFromBytes((OP+":"+(i+1)).getBytes(StandardCharsets.UTF_8))));q.setBytes(p++,bytes(MANUSCRIPT));q.setBytes(p++,branch);q.setBytes(p++,bytes(SCENE));
                        q.setString(p++,SceneGenerationService.toEditorHtml(text));q.setString(p++,json.writeValueAsString(trace));q.setTimestamp(p++,(Timestamp)receipt.get("created_at"));
                        q.setBytes(p++,bytes(OP));q.setInt(p++,i+1);q.setInt(p++,han);q.setBytes(p++,bytes(OP));q.setInt(p,i+1);inserted+=q.executeUpdate();
                    }
                }
                c.commit();System.out.println(json.writeValueAsString(Map.of("operationId",OP,"inserted",inserted,"candidates",receipts.size(),"hanCounts",counts,"newAiCalls",0)));
            } catch(Exception e){c.rollback();throw e;}
        }
    }
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    private static UUID uuid(byte[] bytes){var b=ByteBuffer.wrap(bytes);return new UUID(b.getLong(),b.getLong());}
}
