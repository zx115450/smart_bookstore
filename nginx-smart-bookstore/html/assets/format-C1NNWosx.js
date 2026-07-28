function i(e){if(e==null||e==="")return"¥0.00";const r=typeof e=="number"?e:Number(e);return Number.isFinite(r)?`¥${r.toFixed(2)}`:"¥0.00"}export{i as f};
