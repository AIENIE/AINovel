export type AdminOperationCodePrompt = () => Promise<string | null>;

let promptHandler: AdminOperationCodePrompt | null = null;

export const registerAdminOperationCodePrompt = (handler: AdminOperationCodePrompt) => {
  promptHandler = handler;
  return () => {
    if (promptHandler === handler) promptHandler = null;
  };
};

export const requestAdminOperationCode = async () => {
  if (!promptHandler) {
    throw new Error("二次验证界面尚未就绪");
  }
  return promptHandler();
};
