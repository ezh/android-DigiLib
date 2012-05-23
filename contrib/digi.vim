syn match DATE /^\d\{4}-\d\{2}-\d\{2} \d\{2}:\d\{2}:\d\{2}\.\d\{3}.\{1}\d\{4}\( P\d\{5} T\d\{5} \)\@=/ contained
syn match PIDTID /\(^\d\{4}-\d\{2}-\d\{2} \d\{2}:\d\{2}:\d\{2}\.\d\{3}.\{1}\d\{4}\)\@<= P\d\{5} T\d\{5}/ contained
syn match CLASS /\(^\d\{4}-\d\{2}-\d\{2} \d\{2}:\d\{2}:\d\{2}\.\d\{3}.\{1}\d\{4} P\d\{5} T\d\{5} [^ ]\{1}\)\@<= @[^ ]*/ contained
syn region LogLine start=/^\d\{4}-\d\{2}-\d\{2} \d\{2}:\d\{2}:\d\{2}\.\d\{3}.\{1}\d\{4}\( P\d\{5} T\d\{5} \)\@=/ end=/$/ contains=DATE,PIDTID,CLASS
hi link DATE NonText
hi link PIDTID SignColumn
hi link CLASS Function

