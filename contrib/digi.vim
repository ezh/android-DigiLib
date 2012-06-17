syn match DATE /^.\{28}/ contained nextgroup=PIDTID skipwhite
syn match PIDTID /P\d\{5} T\d\{5}/ contained nextgroup=LEVELT,LEVELD,LEVELI,LEVELW,LEVELE skipwhite

syn keyword LEVELT T contained nextgroup=CLASST skipwhite
syn match CLASST /@[^ ]*/ contained nextgroup=TRACE skipwhite
syn match TRACE /.*/ contained

syn keyword LEVELD D contained nextgroup=CLASSD skipwhite
syn match CLASSD /@[^ ]*/ contained nextgroup=DEBUG skipwhite
syn match DEBUG /.*/ contained

syn keyword LEVELI I contained nextgroup=CLASSI skipwhite
syn match CLASSI /@[^ ]*/ contained nextgroup=INFO skipwhite
syn match INFO /.*/ contained

syn keyword LEVELW W contained nextgroup=CLASSW skipwhite
syn match CLASSW /@[^ ]*/ contained nextgroup=WARNING skipwhite
syn match WARNING /.*/ contained

syn keyword LEVELE E contained nextgroup=CLASSE skipwhite
syn match CLASSE /@[^ ]*/ contained nextgroup=ERROR skipwhite
syn match ERROR /.*/ contained

syn region LogLine start=/^\d\{4}-\d\{2}-\d\{2} \d\{2}:\d\{2}:\d\{2}\.\d\{3}.\{1}\d\{4}\( P\d\{5} T\d\{5} \)\@=/ end=/$/ contains=DATE

hi link DATE NonText
hi link PIDTID SignColumn
hi link CLASS Function

hi link LEVELT Comment
hi link TRACE Comment

hi link LEVELD Statement
hi link DEBUG Statement

hi link LEVELW Directory
hi link WARNING Directory

hi link LEVELE WarningMsg
hi link ERROR WarningMsg
