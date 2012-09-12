Deferred = $.Deferred

# FIXME translate
dcimport.templates.loading = _.template("""<div class="loading">Loading...</div>""")
dcimport.templates.empty = _.template("""<p>You have no projects to import.</p>""")

get_stored_credentials = () ->
  ret = {
    email: sessionStorage.getItem('dcimport_email'),
    password: sessionStorage.getItem('dcimport_password'),
  }

  if ret.email && ret.password
    ret
  else
    undefined

store_credentials = (credentials) ->
  sessionStorage.setItem('dcimport_email', credentials?.email)
  sessionStorage.setItem('dcimport_password', credentials?.password)

credentials_to_authorization_header = (credentials) ->
  "Basic #{Base64.encode64("#{credentials.email}:#{credentials.password}")}"

prompt_for_credentials = (div, is_error=false) ->
  deferred = new Deferred()

  $div = $(div)
  $div.empty()
  $login_form = $(dcimport.templates.login(is_error))
  $div.append($login_form)

  $login_form.one 'submit', (e) ->
    e.preventDefault()

    credentials = {
      email: $login_form.find('input[name=dcimport_email]').val(),
      password: $login_form.find('input[name=dcimport_password]').val(),
    }

    $div.empty()

    deferred.resolve(credentials)

  deferred

request_json_with_login_recursive = (deferred, url, prompt_div, attempt=0) ->
  recurse = (is_error) ->
    d = prompt_for_credentials(prompt_div, is_error)
    d.done (credentials) ->
      store_credentials(credentials)
      request_json_with_login_recursive(deferred, url, prompt_div, attempt + 1)

  credentials = get_stored_credentials()

  if credentials?
    $div = $(prompt_div)
    $div.empty()
    $div.append(dcimport.templates.loading())

    ajax = $.ajax({
      url: url,
      timeout: 20000,
      beforeSend: (xhr) ->
        xhr.setRequestHeader('Authorization', credentials_to_authorization_header(credentials))
    })
    ajax.fail(-> recurse(true))
    ajax.done((json) -> deferred.resolve(json))
  else
    recurse(false)

  undefined

dcimport.request_json_with_login = (url, prompt_div) ->
  deferred = new Deferred()
  request_json_with_login_recursive(deferred, url, prompt_div)
  deferred
