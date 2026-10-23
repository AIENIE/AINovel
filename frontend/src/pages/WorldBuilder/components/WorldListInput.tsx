import { useEffect, useRef, useState } from "react";
import { Input } from "@/components/ui/input";
export function WorldListInput({values,onChange,placeholder,label,disabled}: {values:string[];onChange:(value:string[])=>void;placeholder?:string;label?:string;disabled?:boolean}) {
  const [text,setText]=useState(values.join(", "));const sent=useRef(JSON.stringify(values));
  useEffect(()=>{const value=JSON.stringify(values);if(value!==sent.current){sent.current=value;setText(values.join(", "));}},[values]);
  return <Input aria-label={label} disabled={disabled} value={text} placeholder={placeholder} onChange={e=>{setText(e.target.value);const next=e.target.value.split(/[,，、]/).map(v=>v.trim()).filter(Boolean);sent.current=JSON.stringify(next);onChange(next);}}/>;
}
