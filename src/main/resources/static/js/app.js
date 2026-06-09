async function connectWhatsapp() {

    const response = await fetch(
        "/api/whatsapp/connect?email=" + userEmail,
        {
            method: "POST"
        }
    );

    const code = await response.text();

    alert(
        "Send this code to your WhatsApp bot:\n\n" + code
    );
}