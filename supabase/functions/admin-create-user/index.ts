import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type RequestBody = {
  email: string;
  password: string;
  full_name: string;
  phone?: string;
  role: "admin" | "responder";
};

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const body = await request.json() as RequestBody;
    if (!body.email?.trim() || !body.password || !body.full_name?.trim()) {
      return Response.json({ error: "email, password, and full_name are required" }, { status: 400, headers: cors });
    }
    if (!["admin", "responder"].includes(body.role)) {
      return Response.json({ error: "Invalid role" }, { status: 400, headers: cors });
    }
    const service = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const caller = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: request.headers.get("Authorization") ?? "" } } });
    const { data: callerData } = await caller.auth.getUser();
    if (!callerData.user) return Response.json({ error: "Authentication required" }, { status: 401, headers: cors });
    const { data: admin } = await service.from("profiles").select("role,is_active").eq("id", callerData.user.id).maybeSingle();
    if (!admin || admin.role !== "admin" || !admin.is_active) {
      return Response.json({ error: "Administrator role required" }, { status: 403, headers: cors });
    }
    const { data: created, error: authError } = await service.auth.admin.createUser({
      email: body.email.trim(), password: body.password, email_confirm: true,
    });
    if (authError || !created.user) throw authError ?? new Error("User creation failed");
    const { error: profileError } = await service.from("profiles").insert({
      id: created.user.id, email: body.email.trim(), full_name: body.full_name.trim(),
      phone: body.phone?.trim() || null, role: body.role, is_active: true,
    });
    if (profileError) {
      await service.auth.admin.deleteUser(created.user.id);
      throw profileError;
    }
    return Response.json({ id: created.user.id }, { headers: cors });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Request failed" }, { status: 400, headers: cors });
  }
});
