from werkzeug.security import generate_password_hash

#print(generate_password_hash())


STORED_HASH="pbkdf2:sha256:600000$QILKdu30zMscM9WP$96c300c1b0d6e7da94aaea8344821b69e6c1a917985db74d300677fb74391aca"


def get_stored_password():
    return STORED_HASH