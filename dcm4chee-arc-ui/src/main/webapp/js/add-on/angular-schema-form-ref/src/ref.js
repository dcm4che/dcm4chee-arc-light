angular.module('schemaForm').config(
['schemaFormProvider', 'schemaFormDecoratorsProvider', 'sfPathProvider',
  function(schemaFormProvider,  schemaFormDecoratorsProvider, sfPathProvider) {
    // var ref = function(name, schema, options) {
    // 	if(name==="hl7ApplicationRef"){
    // 		console.log("*******************huraaaaa.....");
    // 		console.log("schema=",schema);
    // 	}
    // 	console.log("name=",name);
    // 	console.log("schema.type=",schema.type);
    // 	console.log("schema.format=",schema.format);
    // 	console.log("options=",options);
    //             if ((schema.type === 'string') && ("$ref" in schema)) {
    //             	console.log("in if ref.js");
    //                 var f = schemaFormProvider.stdFormObj(name, schema, options);
    //                 f.key = options.path;
    //                 f.type = 'ref';
    //                 options.lookup[sfPathProvider.stringify(options.path)] = f;
    //                 return f;
    //             }


    // };
    // console.log("schemaFormProvider.defaults.string=",schemaFormProvider.defaults.string)
    // schemaFormProvider.defaults.string.unshift(ref);

    //Add to the bootstrap directive
    schemaFormDecoratorsProvider.addMapping(
      'bootstrapDecorator',
      'ref',
      'js/add-on/angular-schema-form-ref/src/ref.html'
    );
    schemaFormDecoratorsProvider.createDirective(
      'loadSubSchema',
      'js/add-on/angular-schema-form-ref/src/test.html'
    );
  }
]);